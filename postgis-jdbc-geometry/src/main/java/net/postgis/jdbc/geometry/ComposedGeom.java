/*
 * ComposedGeom.java
 * 
 * PostGIS extension for PostgreSQL JDBC driver - geometry model
 * 
 * (C) 2004 Paul Ramsey, pramsey@refractions.net
 * 
 * (C) 2005 Markus Schaber, markus.schaber@logix-tt.com
 *
 * (C) 2015 Phillip Ross, phillip.w.g.ross@gmail.com
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 * 
 */

package net.postgis.jdbc.geometry;

import java.sql.SQLException;
import java.util.Iterator;
import java.util.List;

/**
 * ComposedGeom - Abstract base class for all Geometries that are composed out
 * of other Geometries.
 * 
 * In fact, this currently are all Geometry subclasses except Point.
 * 
 * @author markus.schaber@logix-tt.com
 * 
 * 
 */
public abstract class ComposedGeom extends Geometry {
    /* JDK 1.5 Serialization */
    private static final long serialVersionUID = 0x100;

    /**
     * The Array containing the geometries
     * 
     * This is only to be exposed by concrete subclasses, to retain type safety.
     */
    protected Geometry[] subgeoms = createSubGeomArray(0);

    /**
     * Constructs an instance with the specified type
     *
     * @param type int value corresponding to the geometry type.
     */
    public ComposedGeom(int type) {
        super(type);
    }

    public Geometry getSubGeometry(int index) {
        return subgeoms[index];
    }

    public int numGeoms() {
        return subgeoms.length;
    }

    protected ComposedGeom(int type, Geometry[] geoms) {
        this(type);
        this.subgeoms = geoms;
        if (geoms.length > 0) {
            dimension = geoms[0].dimension;
            haveMeasure = geoms[0].haveMeasure;
        } else {
            dimension = 0;
        }
    }

    protected ComposedGeom(int type, String value, boolean haveM) throws SQLException {
        super(type);
        value = initSRID(value);

        String typestring = getTypeString();
        if (value.indexOf(typestring) == 0) {
            int pfxlen = typestring.length();
            if (value.charAt(pfxlen) == 'M') {
                pfxlen += 1;
                haveM = true;
            }
            value = value.substring(pfxlen).trim();
        } else if (value.charAt(0) != '(') {
            // we are neigher inner nor outer rep.
            throw new SQLException("Error parsing a " + typestring + " out of " + value);
        }
        if (value.equals("(EMPTY)")) {
            // Special case for PostGIS 0.X style empty geometry collections
            // (which are not OpenGIS compliant)
            return;
        }

        if (value.equals("EMPTY")) {
            // Empty collection
            return;
        }

        String valueNoParans = GeometryTokenizer.removeLeadingAndTrailingStrings(value, "(", ")");
        List<String> tokens = GeometryTokenizer.tokenize(valueNoParans, ',');

        int subgeomcount = tokens.size();
        subgeoms = createSubGeomArray(subgeomcount);
        for (int p = 0; p < subgeomcount; p++) {
            subgeoms[p] = createSubGeomInstance(tokens.get(p), haveM);
        }
        dimension = subgeoms[0].dimension;
        // fetch haveMeasure from sub-point because haveM does only work with
        // 2d+M, not with 3d+M geometries
        haveMeasure = subgeoms[0].haveMeasure;
    }

    /**
     * Return the appropriate instance of the sub-geometry - this encapsulates
     * subclass specific constructor calls
     *
     * @param token The token containing the value for the sub-geometry
     * @param haveM flag to indicate the existence of a measure
     * @return the new sub-geometry
     * @throws SQLException if a SQLException is thrown
     */
    protected abstract Geometry createSubGeomInstance(String token, boolean haveM) throws SQLException;

    /**
     * Return the appropriate instance of the sub-geometry array - this
     * encapsulates subclass specific array instantiation
     *
     * @param size number of elements in the array
     * @return Geometry array corresponding to the sub-geometry
     */
    protected abstract Geometry[] createSubGeomArray(int size);

    protected boolean equalsintern(Geometry other) {
        // Can be assumed to be the same subclass of Geometry, so it must be a
        // ComposedGeom, too.
        ComposedGeom cother = (ComposedGeom) other;

        if (cother.subgeoms == null && subgeoms == null) {
            return true;
        } else if (cother.subgeoms == null || subgeoms == null) {
            return false;
        } else if (cother.subgeoms.length != subgeoms.length) {
            return false;
        } else if (subgeoms.length == 0) {
            return true;
        } else {
            for (int i = 0; i < subgeoms.length; i++) {
                if (!cother.subgeoms[i].equalsintern(this.subgeoms[i])) {
                    return false;
                }
            }
        }
        return true;
    }

    public int numPoints() {
        if ((subgeoms == null) || (subgeoms.length == 0)) {
            return 0;
        } else {
            int result = 0;
            for (int i = 0; i < subgeoms.length; i++) {
                result += subgeoms[i].numPoints();
            }
            return result;
        }
    }

    public Point getPoint(int n) {
        if (n < 0) {
            throw new ArrayIndexOutOfBoundsException("Negative index not allowed");
        } else if ((subgeoms == null) || (subgeoms.length == 0)) {
            throw new ArrayIndexOutOfBoundsException("Empty Geometry has no Points!");
        } else {
            for (int i = 0; i < subgeoms.length; i++) {

                Geometry current = subgeoms[i];
                int np = current.numPoints();
                if (n < np) {
                    return current.getPoint(n);
                } else {
                    n -= np;
                }
            }
            throw new ArrayIndexOutOfBoundsException("Index too large!");
        }
    }

    /**
     * Optimized version
     */
    public Point getLastPoint() {
        if ((subgeoms == null) || (subgeoms.length == 0)) {
            throw new ArrayIndexOutOfBoundsException("Empty Geometry has no Points!");
        } else {
            return subgeoms[subgeoms.length - 1].getLastPoint();
        }
    }

    /**
     * Optimized version
     */
    public Point getFirstPoint() {
        if ((subgeoms == null) || (subgeoms.length == 0)) {
            throw new ArrayIndexOutOfBoundsException("Empty Geometry has no Points!");
        } else {
            return subgeoms[0].getFirstPoint();
        }
    }

    public Iterator iterator() {
        return java.util.Arrays.asList(subgeoms).iterator();
    }

    public boolean isEmpty() {
        return (subgeoms == null) || (subgeoms.length == 0);
    }

    protected void mediumWKT(StringBuffer sb) {
        if ((subgeoms == null) || (subgeoms.length == 0)) {
            sb.append(" EMPTY");
        } else {
            sb.append('(');
            innerWKT(sb);
            sb.append(')');
        }
    }

    protected void innerWKT(StringBuffer sb) {
        subgeoms[0].mediumWKT(sb);
        for (int i = 1; i < subgeoms.length; i++) {
            sb.append(',');
            subgeoms[i].mediumWKT(sb);
        }
    }

    // Hashing - still buggy!
    boolean nohash = true;
    int hashcode = 0;

    public int hashCode() {
        if (nohash) {
            hashcode = super.hashCode() ^ subgeoms.hashCode();
            nohash = false;
        }
        return hashcode;
    }

    public boolean checkConsistency() {
        if (super.checkConsistency()) {
            if (isEmpty()) {
                return true;
            }
            // cache to avoid getMember opcode
            int _dimension = this.dimension;
            boolean _haveMeasure = this.haveMeasure;
            int _srid = this.srid;
            for (int i = 0; i < subgeoms.length; i++) {
                Geometry sub = subgeoms[i];
                if (!(sub.checkConsistency() && sub.dimension == _dimension
                        && sub.haveMeasure == _haveMeasure && sub.srid == _srid)) {
                    return false;
                }
            }
            return true;
        } else {
            return false;
        }
    }

    public void setSrid(int srid) {
        super.setSrid(srid);
        for (int i = 0; i < subgeoms.length; i++) {
            subgeoms[i].setSrid(srid);
        }
    }
}
