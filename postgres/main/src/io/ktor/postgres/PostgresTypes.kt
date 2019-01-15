package io.ktor.postgres

data class PostgresType(
    val oid: Int,
    val name: String,
    val size: Int,
    val description: String? = null

) {
    companion object {
        private val oidToType: Array<PostgresType?>
        private val nameToType: Map<String, PostgresType>

        init {
            val maxOid = rawTypes.maxBy { it.oid }!!.oid
            val dictionary = rawTypes.associateBy { it.oid }
            oidToType = Array(maxOid) { index ->
                dictionary[index]?.let { rawType ->
                    PostgresType(rawType.oid, rawType.typname, rawType.typlen, rawType.descr)
                }
            }
            nameToType = oidToType.filterNotNull().associateBy { it.name }
        }

        fun findType(name: String): PostgresType? {
            return nameToType[name]
        }

        fun getType(name: String): PostgresType {
            return nameToType[name] ?: throw PostgresException("Type with name $name is not known.")
        }

        fun getType(oid: Int): PostgresType {
            return findType(oid) ?: throw PostgresException("Type with OID $oid is not known.")
        }

        fun findType(oid: Int): PostgresType? {
            require(oid >= 0) { "Type OID should be non-negative, but was $oid" }
            if (oid >= oidToType.size)
                return null
            return oidToType[oid]
        }
    }
}


private data class PostgresRawType(
    /* 
     * Type identitier 
     */
    val oid: Int,

    /* 
     * Type name 
     */
    val typname: String,

    /*
	 * For a fixed-size type, typlen is the number of bytes we use to
	 * represent a value of this type, e.g. 4 for an int4.  But for a
	 * variable-length type, typlen is negative.  We use -1 to indicate a
	 * "varlena" type (one that has a length word), -2 to indicate a
	 * null-terminated C string.
	 */

    val typlen: Int,

    /*
     * typbyval determines whether internal Postgres routines pass a value of
     * this type by value or by reference.  typbyval had better be false if
     * the length is not 1, 2, or 4 (or 8 on 8-byte-Datum machines).
     * Variable-length types are always passed by reference. Note that
     * typbyval can be false even if the length would allow pass-by-value; for
     * example, type macaddr8 is pass-by-ref even when Datum is 8 bytes.
     */
    val typbyval: String,

    /*
     * typcategory and typispreferred help the parser distinguish preferred
     * and non-preferred coercions.  The category can be any single ASCII
     * character (but not \0).  The categories used for built-in types are
     * identified by the TYPCATEGORY macros below.
     */
    val typcategory: String,

    /* is type "preferred" within its category? */
    val typispreferred: Boolean = false,

    /* 
     * text format (required) 
     */
    val typinput: String,
    val typoutput: String,

    /* 
     * binary format (optional) 
     */
    val typsend: String,
    val typreceive: String,

    /*
	 * I/O functions for optional type modifiers.
	 */
    val typmodin: String? = null,
    val typmodout: String? = null,

    val typalign: String,

    val oid_symbol: String? = null,
    val descr: String? = null,
    val array_type_oid: Int? = null,
    /* delimiter for arrays of this type */
    val typdelim: String? = null,

    /*
     * If typelem is not 0 then it identifies another row in pg_type. The
     * current type can then be subscripted like an array yielding values of
     * type typelem. A non-zero typelem does not guarantee this type to be a
     * "real" array type; some ordinary fixed-length types can also be
     * subscripted (e.g., name, point). Variable-length types can *not* be
     * turned into pseudo-arrays like that. Hence, the way to determine
     * whether a type is a "true" array type is if:
     *
     * typelem != 0 and typlen == -1.
     */
    val typelem: String? = null,

    val typstorage: String? = null,
    val typcollation: String? = null,

    /*
     * typtype is 'b' for a base type, 'c' for a composite type (e.g., a
     * table's rowtype), 'd' for a domain, 'e' for an enum type, 'p' for a
     * pseudo-type, or 'r' for a range type. (Use the TYPTYPE macros below.)
     *
     * If typtype is 'c', typrelid is the OID of the class' entry in pg_class.
     */
    val typtype: String? = null,

    /* associated pg_class OID if a composite type, else 0 */
    val typrelid: String? = null,

    /*
     * Custom ANALYZE procedure for the datatype (0 selects the default).
     */
    val typanalyze: String? = null,

    /*
     * If there is a "true" array type having this type as element type,
     * typarray links to it.  Zero if no associated "true" array type.
     */
    val typarray: String? = null
)

private const val NAMEDATALEN = 63
private const val SIZEOF_POINTER = 8

// Converted from https://github.com/postgres/postgres/blob/master/src/include/catalog/pg_type.dat
private val rawTypes = listOf(
    PostgresRawType(
        oid = 16, array_type_oid = 1000,
        descr = "boolean, \"true\"/\"false\"",
        typname = "bool", typlen = 1, typbyval = "t", typcategory = "B",
        typispreferred = true, typinput = "boolin", typoutput = "boolout",
        typreceive = "boolrecv", typsend = "boolsend", typalign = "c"
    ),
    PostgresRawType(
        oid = 17, array_type_oid = 1001,
        descr = "variable-length string, binary values escaped",
        typname = "bytea", typlen = -1, typbyval = "f", typcategory = "U",
        typinput = "byteain", typoutput = "byteaout", typreceive = "bytearecv",
        typsend = "byteasend", typalign = "i", typstorage = "x"
    ),
    PostgresRawType(
        oid = 18, array_type_oid = 1002, descr = "single character",
        typname = "char", typlen = 1, typbyval = "t", typcategory = "S",
        typinput = "charin", typoutput = "charout", typreceive = "charrecv",
        typsend = "charsend", typalign = "c"
    ),
    PostgresRawType(
        oid = 19, array_type_oid = 1003,
        descr = "63-byte type for storing system identifiers",
        typname = "name", typlen = NAMEDATALEN, typbyval = "f",
        typcategory = "S", typelem = "char", typinput = "namein",
        typoutput = "nameout", typreceive = "namerecv", typsend = "namesend",
        typalign = "c", typcollation = "950"
    ),
    PostgresRawType(
        oid = 20, array_type_oid = 1016,
        descr = "~18 digit integer, 8-byte storage",
        typname = "int8", typlen = 8, typbyval = "FLOAT8PASSBYVAL",
        typcategory = "N", typinput = "int8in", typoutput = "int8out",
        typreceive = "int8recv", typsend = "int8send", typalign = "d"
    ),
    PostgresRawType(
        oid = 21, array_type_oid = 1005,
        descr = "-32 thousand to 32 thousand, 2-byte storage",
        typname = "int2", typlen = 2, typbyval = "t", typcategory = "N",
        typinput = "int2in", typoutput = "int2out", typreceive = "int2recv",
        typsend = "int2send", typalign = "s"
    ),
    PostgresRawType(
        oid = 22, array_type_oid = 1006,
        descr = "array of int2, used in system tables",
        typname = "int2vector", typlen = -1, typbyval = "f", typcategory = "A",
        typelem = "int2", typinput = "int2vectorin", typoutput = "int2vectorout",
        typreceive = "int2vectorrecv", typsend = "int2vectorsend",
        typalign = "i"
    ),
    PostgresRawType(
        oid = 23, array_type_oid = 1007,
        descr = "-2 billion to 2 billion integer, 4-byte storage",
        typname = "int4", typlen = 4, typbyval = "t", typcategory = "N",
        typinput = "int4in", typoutput = "int4out", typreceive = "int4recv",
        typsend = "int4send", typalign = "i"
    ),
    PostgresRawType(
        oid = 24, array_type_oid = 1008, descr = "registered procedure",
        typname = "regproc", typlen = 4, typbyval = "t", typcategory = "N",
        typinput = "regprocin", typoutput = "regprocout",
        typreceive = "regprocrecv", typsend = "regprocsend", typalign = "i"
    ),
    PostgresRawType(
        oid = 25, array_type_oid = 1009,
        descr = "variable-length string, no limit specified",
        typname = "text", typlen = -1, typbyval = "f", typcategory = "S",
        typispreferred = true, typinput = "textin", typoutput = "textout",
        typreceive = "textrecv", typsend = "textsend", typalign = "i",
        typstorage = "x", typcollation = "100"
    ),
    PostgresRawType(
        oid = 26, array_type_oid = 1028,
        descr = "object identifier(oid), maximum 4 billion",
        typname = "oid", typlen = 4, typbyval = "t", typcategory = "N",
        typispreferred = true, typinput = "oidin", typoutput = "oidout",
        typreceive = "oidrecv", typsend = "oidsend", typalign = "i"
    ),
    PostgresRawType(
        oid = 27, array_type_oid = 1010,
        descr = "(block, offset), physical location of tuple",
        typname = "tid", typlen = 6, typbyval = "f", typcategory = "U",
        typinput = "tidin", typoutput = "tidout", typreceive = "tidrecv",
        typsend = "tidsend", typalign = "s"
    ),
    PostgresRawType(
        oid = 28, array_type_oid = 1011, descr = "transaction id",
        typname = "xid", typlen = 4, typbyval = "t", typcategory = "U",
        typinput = "xidin", typoutput = "xidout", typreceive = "xidrecv",
        typsend = "xidsend", typalign = "i"
    ),
    PostgresRawType(
        oid = 29, array_type_oid = 1012,
        descr = "command identifier type, sequence in transaction id",
        typname = "cid", typlen = 4, typbyval = "t", typcategory = "U",
        typinput = "cidin", typoutput = "cidout", typreceive = "cidrecv",
        typsend = "cidsend", typalign = "i"
    ),
    PostgresRawType(
        oid = 30, array_type_oid = 1013,
        descr = "array of oids, used in system tables",
        typname = "oidvector", typlen = -1, typbyval = "f", typcategory = "A",
        typelem = "oid", typinput = "oidvectorin", typoutput = "oidvectorout",
        typreceive = "oidvectorrecv", typsend = "oidvectorsend", typalign = "i"
    ),

// hand-built rowtype entries for bootstrapped catalogs
// NB: OIDs assigned here must match the BKI_ROWTYPE_OID declarations
    PostgresRawType(
        oid = 71,
        typname = "pg_type", typlen = -1, typbyval = "f", typtype = "c",
        typcategory = "C", typrelid = "1247", typinput = "record_in",
        typoutput = "record_out", typreceive = "record_recv",
        typsend = "record_send", typalign = "d", typstorage = "x"
    ),
    PostgresRawType(
        oid = 75,
        typname = "pg_attribute", typlen = -1, typbyval = "f", typtype = "c",
        typcategory = "C", typrelid = "1249", typinput = "record_in",
        typoutput = "record_out", typreceive = "record_recv",
        typsend = "record_send", typalign = "d", typstorage = "x"
    ),
    PostgresRawType(
        oid = 81,
        typname = "pg_proc", typlen = -1, typbyval = "f", typtype = "c",
        typcategory = "C", typrelid = "1255", typinput = "record_in",
        typoutput = "record_out", typreceive = "record_recv",
        typsend = "record_send", typalign = "d", typstorage = "x"
    ),
    PostgresRawType(
        oid = 83,
        typname = "pg_class", typlen = -1, typbyval = "f", typtype = "c",
        typcategory = "C", typrelid = "1259", typinput = "record_in",
        typoutput = "record_out", typreceive = "record_recv",
        typsend = "record_send", typalign = "d", typstorage = "x"
    ),

// OIDS 100 - 199

    PostgresRawType(
        oid = 114, array_type_oid = 199, descr = "JSON stored as text",
        typname = "json", typlen = -1, typbyval = "f", typcategory = "U",
        typinput = "json_in", typoutput = "json_out", typreceive = "json_recv",
        typsend = "json_send", typalign = "i", typstorage = "x"
    ),
    PostgresRawType(
        oid = 142, array_type_oid = 143, descr = "XML content",
        typname = "xml", typlen = -1, typbyval = "f", typcategory = "U",
        typinput = "xml_in", typoutput = "xml_out", typreceive = "xml_recv",
        typsend = "xml_send", typalign = "i", typstorage = "x"
    ),
    PostgresRawType(
        oid = 194, oid_symbol = "PGNODETREEOID",
        descr = "string representing an internal node tree",
        typname = "pg_node_tree", typlen = -1, typbyval = "f",
        typcategory = "S", typinput = "pg_node_tree_in",
        typoutput = "pg_node_tree_out", typreceive = "pg_node_tree_recv",
        typsend = "pg_node_tree_send", typalign = "i", typstorage = "x",
        typcollation = "100"
    ),
    PostgresRawType(
        oid = 3361, oid_symbol = "PGNDISTINCTOID",
        descr = "multivariate ndistinct coefficients",
        typname = "pg_ndistinct", typlen = -1, typbyval = "f",
        typcategory = "S", typinput = "pg_ndistinct_in",
        typoutput = "pg_ndistinct_out", typreceive = "pg_ndistinct_recv",
        typsend = "pg_ndistinct_send", typalign = "i", typstorage = "x",
        typcollation = "100"
    ),
    PostgresRawType(
        oid = 3402, oid_symbol = "PGDEPENDENCIESOID",
        descr = "multivariate dependencies",
        typname = "pg_dependencies", typlen = -1, typbyval = "f",
        typcategory = "S", typinput = "pg_dependencies_in",
        typoutput = "pg_dependencies_out", typreceive = "pg_dependencies_recv",
        typsend = "pg_dependencies_send", typalign = "i", typstorage = "x",
        typcollation = "100"
    ),
    PostgresRawType(
        oid = 32, oid_symbol = "PGDDLCOMMANDOID",
        descr = "internal type for passing CollectedCommand",
        typname = "pg_ddl_command", typlen = SIZEOF_POINTER, typbyval = "t",
        typtype = "p", typcategory = "P", typinput = "pg_ddl_command_in",
        typoutput = "pg_ddl_command_out", typreceive = "pg_ddl_command_recv",
        typsend = "pg_ddl_command_send", typalign = "ALIGNOF_POINTER"
    ),

// OIDS 200 - 299

    PostgresRawType(
        oid = 210, descr = "storage manager",
        typname = "smgr", typlen = 2, typbyval = "t", typcategory = "U",
        typinput = "smgrin", typoutput = "smgrout", typreceive = "-",
        typsend = "-", typalign = "s"
    ),

// OIDS 600 - 699

    PostgresRawType(
        oid = 600, array_type_oid = 1017,
        descr = "geometric point \"(x, y)\"",
        typname = "point", typlen = 16, typbyval = "f", typcategory = "G",
        typelem = "float8", typinput = "point_in", typoutput = "point_out",
        typreceive = "point_recv", typsend = "point_send", typalign = "d"
    ),
    PostgresRawType(
        oid = 601, array_type_oid = 1018,
        descr = "geometric line segment \"(pt1,pt2)\"",
        typname = "lseg", typlen = 32, typbyval = "f", typcategory = "G",
        typelem = "point", typinput = "lseg_in", typoutput = "lseg_out",
        typreceive = "lseg_recv", typsend = "lseg_send", typalign = "d"
    ),
    PostgresRawType(
        oid = 602, array_type_oid = 1019,
        descr = "geometric path \"(pt1,...)\"",
        typname = "path", typlen = -1, typbyval = "f", typcategory = "G",
        typinput = "path_in", typoutput = "path_out", typreceive = "path_recv",
        typsend = "path_send", typalign = "d", typstorage = "x"
    ),
    PostgresRawType(
        oid = 603, array_type_oid = 1020,
        descr = "geometric box \"(lower left,upper right)\"",
        typname = "box", typlen = 32, typbyval = "f", typcategory = "G",
        typdelim = ";", typelem = "point", typinput = "box_in",
        typoutput = "box_out", typreceive = "box_recv", typsend = "box_send",
        typalign = "d"
    ),
    PostgresRawType(
        oid = 604, array_type_oid = 1027,
        descr = "geometric polygon \"(pt1,...)\"",
        typname = "polygon", typlen = -1, typbyval = "f", typcategory = "G",
        typinput = "poly_in", typoutput = "poly_out", typreceive = "poly_recv",
        typsend = "poly_send", typalign = "d", typstorage = "x"
    ),
    PostgresRawType(
        oid = 628, array_type_oid = 629, descr = "geometric line",
        typname = "line", typlen = 24, typbyval = "f", typcategory = "G",
        typelem = "float8", typinput = "line_in", typoutput = "line_out",
        typreceive = "line_recv", typsend = "line_send", typalign = "d"
    ),

// OIDS 700 - 799

    PostgresRawType(
        oid = 700, array_type_oid = 1021,
        descr = "single-precision floating point number, 4-byte storage",
        typname = "float4", typlen = 4, typbyval = "FLOAT4PASSBYVAL",
        typcategory = "N", typinput = "float4in", typoutput = "float4out",
        typreceive = "float4recv", typsend = "float4send", typalign = "i"
    ),
    PostgresRawType(
        oid = 701, array_type_oid = 1022,
        descr = "double-precision floating point number, 8-byte storage",
        typname = "float8", typlen = 8, typbyval = "FLOAT8PASSBYVAL",
        typcategory = "N", typispreferred = true, typinput = "float8in",
        typoutput = "float8out", typreceive = "float8recv", typsend = "float8send",
        typalign = "d"
    ),
    PostgresRawType(
        oid = 705, descr = "pseudo-type representing an undetermined type",
        typname = "unknown", typlen = -2, typbyval = "f", typtype = "p",
        typcategory = "X", typinput = "unknownin", typoutput = "unknownout",
        typreceive = "unknownrecv", typsend = "unknownsend", typalign = "c"
    ),
    PostgresRawType(
        oid = 718, array_type_oid = 719,
        descr = "geometric circle \"(center,radius)\"",
        typname = "circle", typlen = 24, typbyval = "f", typcategory = "G",
        typinput = "circle_in", typoutput = "circle_out",
        typreceive = "circle_recv", typsend = "circle_send", typalign = "d"
    ),
    PostgresRawType(
        oid = 790, oid_symbol = "CASHOID", array_type_oid = 791,
        descr = "monetary amounts, \$d,ddd.cc",
        typname = "money", typlen = 8, typbyval = "FLOAT8PASSBYVAL",
        typcategory = "N", typinput = "cash_in", typoutput = "cash_out",
        typreceive = "cash_recv", typsend = "cash_send", typalign = "d"
    ),

// OIDS 800 - 899

    PostgresRawType(
        oid = 829, array_type_oid = 1040,
        descr = "XX:XX:XX:XX:XX:XX, MAC address",
        typname = "macaddr", typlen = 6, typbyval = "f", typcategory = "U",
        typinput = "macaddr_in", typoutput = "macaddr_out",
        typreceive = "macaddr_recv", typsend = "macaddr_send", typalign = "i"
    ),
    PostgresRawType(
        oid = 869, array_type_oid = 1041,
        descr = "IP address/netmask, host address, netmask optional",
        typname = "inet", typlen = -1, typbyval = "f", typcategory = "I",
        typispreferred = true, typinput = "inet_in", typoutput = "inet_out",
        typreceive = "inet_recv", typsend = "inet_send", typalign = "i",
        typstorage = "m"
    ),
    PostgresRawType(
        oid = 650, array_type_oid = 651,
        descr = "network IP address/netmask, network address",
        typname = "cidr", typlen = -1, typbyval = "f", typcategory = "I",
        typinput = "cidr_in", typoutput = "cidr_out", typreceive = "cidr_recv",
        typsend = "cidr_send", typalign = "i", typstorage = "m"
    ),
    PostgresRawType(
        oid = 774, array_type_oid = 775,
        descr = "XX:XX:XX:XX:XX:XX:XX:XX, MAC address",
        typname = "macaddr8", typlen = 8, typbyval = "f", typcategory = "U",
        typinput = "macaddr8_in", typoutput = "macaddr8_out",
        typreceive = "macaddr8_recv", typsend = "macaddr8_send", typalign = "i"
    ),

// OIDS 1000 - 1099

    PostgresRawType(
        oid = 1033, array_type_oid = 1034, descr = "access control list",
        typname = "aclitem", typlen = 12, typbyval = "f", typcategory = "U",
        typinput = "aclitemin", typoutput = "aclitemout", typreceive = "-",
        typsend = "-", typalign = "i"
    ),
    PostgresRawType(
        oid = 1042, array_type_oid = 1014,
        descr = "char(length), blank-padded string, fixed storage length",
        typname = "bpchar", typlen = -1, typbyval = "f", typcategory = "S",
        typinput = "bpcharin", typoutput = "bpcharout", typreceive = "bpcharrecv",
        typsend = "bpcharsend", typmodin = "bpchartypmodin",
        typmodout = "bpchartypmodout", typalign = "i", typstorage = "x",
        typcollation = "100"
    ),
    PostgresRawType(
        oid = 1043, array_type_oid = 1015,
        descr = "varchar(length), non-blank-padded string, variable storage length",
        typname = "varchar", typlen = -1, typbyval = "f", typcategory = "S",
        typinput = "varcharin", typoutput = "varcharout",
        typreceive = "varcharrecv", typsend = "varcharsend",
        typmodin = "varchartypmodin", typmodout = "varchartypmodout",
        typalign = "i", typstorage = "x", typcollation = "100"
    ),
    PostgresRawType(
        oid = 1082, array_type_oid = 1182, descr = "date",
        typname = "date", typlen = 4, typbyval = "t", typcategory = "D",
        typinput = "date_in", typoutput = "date_out", typreceive = "date_recv",
        typsend = "date_send", typalign = "i"
    ),
    PostgresRawType(
        oid = 1083, array_type_oid = 1183, descr = "time of day",
        typname = "time", typlen = 8, typbyval = "FLOAT8PASSBYVAL",
        typcategory = "D", typinput = "time_in", typoutput = "time_out",
        typreceive = "time_recv", typsend = "time_send", typmodin = "timetypmodin",
        typmodout = "timetypmodout", typalign = "d"
    ),

// OIDS 1100 - 1199

    PostgresRawType(
        oid = 1114, array_type_oid = 1115, descr = "date and time",
        typname = "timestamp", typlen = 8, typbyval = "FLOAT8PASSBYVAL",
        typcategory = "D", typinput = "timestamp_in", typoutput = "timestamp_out",
        typreceive = "timestamp_recv", typsend = "timestamp_send",
        typmodin = "timestamptypmodin", typmodout = "timestamptypmodout",
        typalign = "d"
    ),
    PostgresRawType(
        oid = 1184, array_type_oid = 1185,
        descr = "date and time with time zone",
        typname = "timestamptz", typlen = 8, typbyval = "FLOAT8PASSBYVAL",
        typcategory = "D", typispreferred = true, typinput = "timestamptz_in",
        typoutput = "timestamptz_out", typreceive = "timestamptz_recv",
        typsend = "timestamptz_send", typmodin = "timestamptztypmodin",
        typmodout = "timestamptztypmodout", typalign = "d"
    ),
    PostgresRawType(
        oid = 1186, array_type_oid = 1187,
        descr = "@ <number> <units>, time interval",
        typname = "interval", typlen = 16, typbyval = "f", typcategory = "T",
        typispreferred = true, typinput = "interval_in", typoutput = "interval_out",
        typreceive = "interval_recv", typsend = "interval_send",
        typmodin = "intervaltypmodin", typmodout = "intervaltypmodout",
        typalign = "d"
    ),

// OIDS 1200 - 1299

    PostgresRawType(
        oid = 1266, array_type_oid = 1270,
        descr = "time of day with time zone",
        typname = "timetz", typlen = 12, typbyval = "f", typcategory = "D",
        typinput = "timetz_in", typoutput = "timetz_out",
        typreceive = "timetz_recv", typsend = "timetz_send",
        typmodin = "timetztypmodin", typmodout = "timetztypmodout",
        typalign = "d"
    ),

// OIDS 1500 - 1599

    PostgresRawType(
        oid = 1560, array_type_oid = 1561, descr = "fixed-length bit string",
        typname = "bit", typlen = -1, typbyval = "f", typcategory = "V",
        typinput = "bit_in", typoutput = "bit_out", typreceive = "bit_recv",
        typsend = "bit_send", typmodin = "bittypmodin", typmodout = "bittypmodout",
        typalign = "i", typstorage = "x"
    ),
    PostgresRawType(
        oid = 1562, array_type_oid = 1563,
        descr = "variable-length bit string",
        typname = "varbit", typlen = -1, typbyval = "f", typcategory = "V",
        typispreferred = true, typinput = "varbit_in", typoutput = "varbit_out",
        typreceive = "varbit_recv", typsend = "varbit_send",
        typmodin = "varbittypmodin", typmodout = "varbittypmodout", typalign = "i",
        typstorage = "x"
    ),

// OIDS 1700 - 1799

    PostgresRawType(
        oid = 1700, array_type_oid = 1231,
        descr = "numeric(precision, decimal), arbitrary precision number",
        typname = "numeric", typlen = -1, typbyval = "f", typcategory = "N",
        typinput = "numeric_in", typoutput = "numeric_out",
        typreceive = "numeric_recv", typsend = "numeric_send",
        typmodin = "numerictypmodin", typmodout = "numerictypmodout",
        typalign = "i", typstorage = "m"
    ),

    PostgresRawType(
        oid = 1790, array_type_oid = 2201,
        descr = "reference to cursor (portal name)",
        typname = "refcursor", typlen = -1, typbyval = "f", typcategory = "U",
        typinput = "textin", typoutput = "textout", typreceive = "textrecv",
        typsend = "textsend", typalign = "i", typstorage = "x"
    ),

// OIDS 2200 - 2299

    PostgresRawType(
        oid = 2202, array_type_oid = 2207,
        descr = "registered procedure (with args)",
        typname = "regprocedure", typlen = 4, typbyval = "t", typcategory = "N",
        typinput = "regprocedurein", typoutput = "regprocedureout",
        typreceive = "regprocedurerecv", typsend = "regproceduresend",
        typalign = "i"
    ),
    PostgresRawType(
        oid = 2203, array_type_oid = 2208, descr = "registered operator",
        typname = "regoper", typlen = 4, typbyval = "t", typcategory = "N",
        typinput = "regoperin", typoutput = "regoperout",
        typreceive = "regoperrecv", typsend = "regopersend", typalign = "i"
    ),
    PostgresRawType(
        oid = 2204, array_type_oid = 2209,
        descr = "registered operator (with args)",
        typname = "regoperator", typlen = 4, typbyval = "t", typcategory = "N",
        typinput = "regoperatorin", typoutput = "regoperatorout",
        typreceive = "regoperatorrecv", typsend = "regoperatorsend",
        typalign = "i"
    ),
    PostgresRawType(
        oid = 2205, array_type_oid = 2210, descr = "registered class",
        typname = "regclass", typlen = 4, typbyval = "t", typcategory = "N",
        typinput = "regclassin", typoutput = "regclassout",
        typreceive = "regclassrecv", typsend = "regclasssend", typalign = "i"
    ),
    PostgresRawType(
        oid = 2206, array_type_oid = 2211, descr = "registered type",
        typname = "regtype", typlen = 4, typbyval = "t", typcategory = "N",
        typinput = "regtypein", typoutput = "regtypeout",
        typreceive = "regtyperecv", typsend = "regtypesend", typalign = "i"
    ),
    PostgresRawType(
        oid = 4096, array_type_oid = 4097, descr = "registered role",
        typname = "regrole", typlen = 4, typbyval = "t", typcategory = "N",
        typinput = "regrolein", typoutput = "regroleout",
        typreceive = "regrolerecv", typsend = "regrolesend", typalign = "i"
    ),
    PostgresRawType(
        oid = 4089, array_type_oid = 4090, descr = "registered namespace",
        typname = "regnamespace", typlen = 4, typbyval = "t", typcategory = "N",
        typinput = "regnamespacein", typoutput = "regnamespaceout",
        typreceive = "regnamespacerecv", typsend = "regnamespacesend",
        typalign = "i"
    ),

// uuid
    PostgresRawType(
        oid = 2950, array_type_oid = 2951, descr = "UUID datatype",
        typname = "uuid", typlen = 16, typbyval = "f", typcategory = "U",
        typinput = "uuid_in", typoutput = "uuid_out", typreceive = "uuid_recv",
        typsend = "uuid_send", typalign = "c"
    ),

// pg_lsn
    PostgresRawType(
        oid = 3220, oid_symbol = "LSNOID", array_type_oid = 3221,
        descr = "PostgreSQL LSN datatype",
        typname = "pg_lsn", typlen = 8, typbyval = "FLOAT8PASSBYVAL",
        typcategory = "U", typinput = "pg_lsn_in", typoutput = "pg_lsn_out",
        typreceive = "pg_lsn_recv", typsend = "pg_lsn_send", typalign = "d"
    ),

// text search
    PostgresRawType(
        oid = 3614, array_type_oid = 3643,
        descr = "text representation for text search",
        typname = "tsvector", typlen = -1, typbyval = "f", typcategory = "U",
        typinput = "tsvectorin", typoutput = "tsvectorout",
        typreceive = "tsvectorrecv", typsend = "tsvectorsend",
        typanalyze = "ts_typanalyze", typalign = "i", typstorage = "x"
    ),
    PostgresRawType(
        oid = 3642, array_type_oid = 3644,
        descr = "GiST index internal text representation for text search",
        typname = "gtsvector", typlen = -1, typbyval = "f", typcategory = "U",
        typinput = "gtsvectorin", typoutput = "gtsvectorout", typreceive = "-",
        typsend = "-", typalign = "i"
    ),
    PostgresRawType(
        oid = 3615, array_type_oid = 3645,
        descr = "query representation for text search",
        typname = "tsquery", typlen = -1, typbyval = "f", typcategory = "U",
        typinput = "tsqueryin", typoutput = "tsqueryout",
        typreceive = "tsqueryrecv", typsend = "tsquerysend", typalign = "i"
    ),
    PostgresRawType(
        oid = 3734, array_type_oid = 3735,
        descr = "registered text search configuration",
        typname = "regconfig", typlen = 4, typbyval = "t", typcategory = "N",
        typinput = "regconfigin", typoutput = "regconfigout",
        typreceive = "regconfigrecv", typsend = "regconfigsend", typalign = "i"
    ),
    PostgresRawType(
        oid = 3769, array_type_oid = 3770,
        descr = "registered text search dictionary",
        typname = "regdictionary", typlen = 4, typbyval = "t",
        typcategory = "N", typinput = "regdictionaryin",
        typoutput = "regdictionaryout", typreceive = "regdictionaryrecv",
        typsend = "regdictionarysend", typalign = "i"
    ),

// jsonb
    PostgresRawType(
        oid = 3802, array_type_oid = 3807, descr = "Binary JSON",
        typname = "jsonb", typlen = -1, typbyval = "f", typcategory = "U",
        typinput = "jsonb_in", typoutput = "jsonb_out", typreceive = "jsonb_recv",
        typsend = "jsonb_send", typalign = "i", typstorage = "x"
    ),

    PostgresRawType(
        oid = 2970, array_type_oid = 2949, descr = "txid snapshot",
        typname = "txid_snapshot", typlen = -1, typbyval = "f",
        typcategory = "U", typinput = "txid_snapshot_in",
        typoutput = "txid_snapshot_out", typreceive = "txid_snapshot_recv",
        typsend = "txid_snapshot_send", typalign = "d", typstorage = "x"
    ),

// range types
    PostgresRawType(
        oid = 3904, array_type_oid = 3905, descr = "range of integers",
        typname = "int4range", typlen = -1, typbyval = "f", typtype = "r",
        typcategory = "R", typinput = "range_in", typoutput = "range_out",
        typreceive = "range_recv", typsend = "range_send",
        typanalyze = "range_typanalyze", typalign = "i", typstorage = "x"
    ),
    PostgresRawType(
        oid = 3906, array_type_oid = 3907, descr = "range of numerics",
        typname = "numrange", typlen = -1, typbyval = "f", typtype = "r",
        typcategory = "R", typinput = "range_in", typoutput = "range_out",
        typreceive = "range_recv", typsend = "range_send",
        typanalyze = "range_typanalyze", typalign = "i", typstorage = "x"
    ),
    PostgresRawType(
        oid = 3908, array_type_oid = 3909,
        descr = "range of timestamps without time zone",
        typname = "tsrange", typlen = -1, typbyval = "f", typtype = "r",
        typcategory = "R", typinput = "range_in", typoutput = "range_out",
        typreceive = "range_recv", typsend = "range_send",
        typanalyze = "range_typanalyze", typalign = "d", typstorage = "x"
    ),
    PostgresRawType(
        oid = 3910, array_type_oid = 3911,
        descr = "range of timestamps with time zone",
        typname = "tstzrange", typlen = -1, typbyval = "f", typtype = "r",
        typcategory = "R", typinput = "range_in", typoutput = "range_out",
        typreceive = "range_recv", typsend = "range_send",
        typanalyze = "range_typanalyze", typalign = "d", typstorage = "x"
    ),
    PostgresRawType(
        oid = 3912, array_type_oid = 3913, descr = "range of dates",
        typname = "daterange", typlen = -1, typbyval = "f", typtype = "r",
        typcategory = "R", typinput = "range_in", typoutput = "range_out",
        typreceive = "range_recv", typsend = "range_send",
        typanalyze = "range_typanalyze", typalign = "i", typstorage = "x"
    ),
    PostgresRawType(
        oid = 3926, array_type_oid = 3927, descr = "range of bigints",
        typname = "int8range", typlen = -1, typbyval = "f", typtype = "r",
        typcategory = "R", typinput = "range_in", typoutput = "range_out",
        typreceive = "range_recv", typsend = "range_send",
        typanalyze = "range_typanalyze", typalign = "d", typstorage = "x"
    ),

// pseudo-types
// types with typtype="p" represent various special cases in the type system.
// These cannot be used to define table columns, but are valid as function
// argument and result types (if supported by the function"s implementation
// language).
// Note: cstring is a borderline case; it is still considered a pseudo-type,
// but there is now support for it in records and arrays.  Perhaps we should
// just treat it as a regular base type?

    PostgresRawType(
        oid = 2249, descr = "pseudo-type representing any composite type",
        typname = "record", typlen = -1, typbyval = "f", typtype = "p",
        typcategory = "P", typarray = "_record", typinput = "record_in",
        typoutput = "record_out", typreceive = "record_recv",
        typsend = "record_send", typalign = "d", typstorage = "x"
    ),
// Arrays of records have typcategory P, so they can"t be autogenerated.
    PostgresRawType(
        oid = 2287,
        typname = "_record", typlen = -1, typbyval = "f", typtype = "p",
        typcategory = "P", typelem = "record", typinput = "array_in",
        typoutput = "array_out", typreceive = "array_recv", typsend = "array_send",
        typanalyze = "array_typanalyze", typalign = "d", typstorage = "x"
    ),
    PostgresRawType(
        oid = 2275, array_type_oid = 1263, descr = "C-style string",
        typname = "cstring", typlen = -2, typbyval = "f", typtype = "p",
        typcategory = "P", typinput = "cstring_in", typoutput = "cstring_out",
        typreceive = "cstring_recv", typsend = "cstring_send", typalign = "c"
    ),
    PostgresRawType(
        oid = 2276, descr = "pseudo-type representing any type",
        typname = "any", typlen = 4, typbyval = "t", typtype = "p",
        typcategory = "P", typinput = "any_in", typoutput = "any_out",
        typreceive = "-", typsend = "-", typalign = "i"
    ),
    PostgresRawType(
        oid = 2277, descr = "pseudo-type representing a polymorphic array type",
        typname = "anyarray", typlen = -1, typbyval = "f", typtype = "p",
        typcategory = "P", typinput = "anyarray_in", typoutput = "anyarray_out",
        typreceive = "anyarray_recv", typsend = "anyarray_send", typalign = "d",
        typstorage = "x"
    ),
    PostgresRawType(
        oid = 2278,
        descr = "pseudo-type for the result of a function with no real result",
        typname = "void", typlen = 4, typbyval = "t", typtype = "p",
        typcategory = "P", typinput = "void_in", typoutput = "void_out",
        typreceive = "void_recv", typsend = "void_send", typalign = "i"
    ),
    PostgresRawType(
        oid = 2279, descr = "pseudo-type for the result of a trigger function",
        typname = "trigger", typlen = 4, typbyval = "t", typtype = "p",
        typcategory = "P", typinput = "trigger_in", typoutput = "trigger_out",
        typreceive = "-", typsend = "-", typalign = "i"
    ),
    PostgresRawType(
        oid = 3838, oid_symbol = "EVTTRIGGEROID",
        descr = "pseudo-type for the result of an event trigger function",
        typname = "event_trigger", typlen = 4, typbyval = "t", typtype = "p",
        typcategory = "P", typinput = "event_trigger_in",
        typoutput = "event_trigger_out", typreceive = "-", typsend = "-",
        typalign = "i"
    ),
    PostgresRawType(
        oid = 2280,
        descr = "pseudo-type for the result of a language handler function",
        typname = "language_handler", typlen = 4, typbyval = "t", typtype = "p",
        typcategory = "P", typinput = "language_handler_in",
        typoutput = "language_handler_out", typreceive = "-", typsend = "-",
        typalign = "i"
    ),
    PostgresRawType(
        oid = 2281,
        descr = "pseudo-type representing an internal data structure",
        typname = "internal", typlen = SIZEOF_POINTER, typbyval = "t",
        typtype = "p", typcategory = "P", typinput = "internal_in",
        typoutput = "internal_out", typreceive = "-", typsend = "-",
        typalign = "ALIGNOF_POINTER"
    ),
    PostgresRawType(
        oid = 2282, descr = "obsolete, deprecated pseudo-type",
        typname = "opaque", typlen = 4, typbyval = "t", typtype = "p",
        typcategory = "P", typinput = "opaque_in", typoutput = "opaque_out",
        typreceive = "-", typsend = "-", typalign = "i"
    ),
    PostgresRawType(
        oid = 2283, descr = "pseudo-type representing a polymorphic base type",
        typname = "anyelement", typlen = 4, typbyval = "t", typtype = "p",
        typcategory = "P", typinput = "anyelement_in",
        typoutput = "anyelement_out", typreceive = "-", typsend = "-",
        typalign = "i"
    ),
    PostgresRawType(
        oid = 2776,
        descr = "pseudo-type representing a polymorphic base type that is not an array",
        typname = "anynonarray", typlen = 4, typbyval = "t", typtype = "p",
        typcategory = "P", typinput = "anynonarray_in",
        typoutput = "anynonarray_out", typreceive = "-", typsend = "-",
        typalign = "i"
    ),
    PostgresRawType(
        oid = 3500,
        descr = "pseudo-type representing a polymorphic base type that is an enum",
        typname = "anyenum", typlen = 4, typbyval = "t", typtype = "p",
        typcategory = "P", typinput = "anyenum_in", typoutput = "anyenum_out",
        typreceive = "-", typsend = "-", typalign = "i"
    ),
    PostgresRawType(
        oid = 3115,
        descr = "pseudo-type for the result of an FDW handler function",
        typname = "fdw_handler", typlen = 4, typbyval = "t", typtype = "p",
        typcategory = "P", typinput = "fdw_handler_in",
        typoutput = "fdw_handler_out", typreceive = "-", typsend = "-",
        typalign = "i"
    ),
    PostgresRawType(
        oid = 325,
        descr = "pseudo-type for the result of an index AM handler function",
        typname = "index_am_handler", typlen = 4, typbyval = "t", typtype = "p",
        typcategory = "P", typinput = "index_am_handler_in",
        typoutput = "index_am_handler_out", typreceive = "-", typsend = "-",
        typalign = "i"
    ),
    PostgresRawType(
        oid = 3310,
        descr = "pseudo-type for the result of a tablesample method function",
        typname = "tsm_handler", typlen = 4, typbyval = "t", typtype = "p",
        typcategory = "P", typinput = "tsm_handler_in",
        typoutput = "tsm_handler_out", typreceive = "-", typsend = "-",
        typalign = "i"
    ),
    PostgresRawType(
        oid = 3831,
        descr = "pseudo-type representing a polymorphic base type that is a range",
        typname = "anyrange", typlen = -1, typbyval = "f", typtype = "p",
        typcategory = "P", typinput = "anyrange_in", typoutput = "anyrange_out",
        typreceive = "-", typsend = "-", typalign = "d", typstorage = "x"
    )
)

