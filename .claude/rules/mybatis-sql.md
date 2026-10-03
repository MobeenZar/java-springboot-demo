---
paths:
  - "src/main/resources/mapper/**/*.xml"
  - "src/main/java/**/*Mapper.java"
  - "src/test/resources/*.sql"
---

# MyBatis & SQL conventions

- SQL lives in XML mappers under `resources/mapper/`, not in `@Select` annotations. The mapper interface holds only
  signatures with Javadoc.
- Bind with `#{param,jdbcType=...}` always. `${...}` string substitution is forbidden (SQL injection). For dynamic
  identifiers, whitelist them in Java first.
- Name every mapper parameter with `@Param`. Single-row lookups return `Optional<T>`.
- Use explicit column aliases plus a `<resultMap>` with an `<id>` element so joins collapse correctly. Don't use `SELECT *`.
- Schema is owned by the DBA: the app never runs DDL against Oracle. If a query needs a new column, update
  `test-schema.sql` to mirror it and call out the schema dependency in the change summary.
- Write portable SQL that runs on both Oracle and H2's Oracle mode. Use `FETCH FIRST n ROWS ONLY` for limits, not `ROWNUM` tricks.
- Mapper result types are mutable JavaBeans (MyBatis needs setters for nested collections). Convert them to
  immutable domain/DTO types before they leave the service layer.
