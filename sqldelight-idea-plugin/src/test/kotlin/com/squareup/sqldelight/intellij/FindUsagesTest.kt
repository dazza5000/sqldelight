package com.squareup.sqldelight.intellij

import com.alecstrong.sqlite.psi.core.psi.SqliteColumnAlias
import com.alecstrong.sqlite.psi.core.psi.SqliteColumnName
import com.alecstrong.sqlite.psi.core.psi.SqliteTableAlias
import com.alecstrong.sqlite.psi.core.psi.SqliteTableName
import com.alecstrong.sqlite.psi.core.psi.SqliteViewName
import com.google.common.truth.Truth.assertThat
import com.intellij.psi.PsiReferenceExpression
import com.intellij.usageView.UsageInfo
import com.squareup.sqldelight.core.lang.SqlDelightFileType
import com.squareup.sqldelight.core.lang.psi.StmtIdentifierMixin
import org.jetbrains.kotlin.idea.findUsages.KotlinReferenceUsageInfo
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstance

class FindUsagesTest : SqlDelightProjectTestCase() {
  fun testFindsBothKotlinAndJavaUsages() {
    myFixture.openFileInEditor(
        tempRoot.findFileByRelativePath("src/main/java/com/example/SampleClass.java")!!
    )
    val javaCallsite = searchForElement<PsiReferenceExpression>("someQuery").single()

    myFixture.openFileInEditor(
        tempRoot.findFileByRelativePath("src/main/kotlin/com/example/KotlinClass.kt")!!
    )
    val kotlinCallsite = searchForElement<KtReferenceExpression>("someQuery").single()

    myFixture.openFileInEditor(
        tempRoot.findFileByRelativePath("src/main/sqldelight/com/example/Main.sq")!!
    )
    val identifier = searchForElement<StmtIdentifierMixin>("someQuery").single()
    assertThat(myFixture.findUsages(identifier)).containsExactly(
        KotlinReferenceUsageInfo(javaCallsite),
        KotlinReferenceUsageInfo(kotlinCallsite.references.firstIsInstance())
    )
  }

  fun testFindsUsagesOfAllGeneratedMethods() {
    myFixture.openFileInEditor(
        tempRoot.findFileByRelativePath("src/main/kotlin/com/example/KotlinClass.kt")!!
    )
    val callsites = searchForElement<KtReferenceExpression>("multiQuery")

    myFixture.openFileInEditor(
        tempRoot.findFileByRelativePath("src/main/sqldelight/com/example/Main.sq")!!
    )
    val identifier = searchForElement<StmtIdentifierMixin>("multiQuery").single()
    assertThat(myFixture.findUsages(identifier)).containsExactly(*callsites.map {
      KotlinReferenceUsageInfo(it.references.firstIsInstance())
    }.toTypedArray())
  }

  fun testFindsUsagesOfTable() {
    myFixture.configureByText(SqlDelightFileType, """
      |CREATE TABLE test (
      |  value TEXT NOT NULL
      |);
      |
      |INSERT INTO test
      |VALUES ('stuff');
      |
      |someSelect:
      |SELECT *
      |FROM test;
    """.trimMargin())
    val tableName = searchForElement<SqliteTableName>("test")
    assertThat(tableName).hasSize(3)

    assertThat(myFixture.findUsages(tableName.first())).containsExactly(*tableName.map {
      UsageInfo(it)
    }.toTypedArray())
  }

  fun testFindsUsagesOfColumn() {
    myFixture.configureByText(SqlDelightFileType, """
      |CREATE TABLE test (
      |  value TEXT NOT NULL
      |);
      |
      |INSERT INTO test (value)
      |VALUES ('stuff');
      |
      |anUpdate:
      |UPDATE test
      |SET value = ?;
      |
      |someSelect:
      |SELECT value
      |FROM test
      |WHERE test.value = ?;
    """.trimMargin())
    val tableName = searchForElement<SqliteColumnName>("value")
    assertThat(tableName).hasSize(5)

    assertThat(myFixture.findUsages(tableName.first())).containsExactly(*tableName.map {
      UsageInfo(it)
    }.toTypedArray())
  }

  fun testFindsUsagesOfView() {
    myFixture.configureByText(SqlDelightFileType, """
      |CREATE VIEW test AS
      |SELECT 1, 2;
      |
      |someSelect:
      |SELECT *
      |FROM test;
    """.trimMargin())
    val viewName = searchForElement<SqliteViewName>("test") +
        searchForElement<SqliteTableName>("test")
    assertThat(viewName).hasSize(2)

    assertThat(myFixture.findUsages(viewName.first())).containsExactly(*viewName.map {
      UsageInfo(it)
    }.toTypedArray())
  }

  fun testFindsUsagesOfColumnAlias() {
    myFixture.configureByText(SqlDelightFileType, """
      |CREATE TABLE test (
      |  stuff TEXT NOT NULL
      |);
      |
      |CREATE VIEW test_view AS
      |SELECT stuff AS stuff_alias
      |FROM test;
      |
      |someSelect:
      |SELECT stuff_alias
      |FROM test_view;
    """.trimMargin())
    val columnAlias = searchForElement<SqliteColumnAlias>("stuff_alias") +
        searchForElement<SqliteColumnName>("stuff_alias")
    assertThat(columnAlias).hasSize(2)

    assertThat(myFixture.findUsages(columnAlias.first())).containsExactly(*columnAlias.drop(1).map {
      UsageInfo(it)
    }.toTypedArray())
  }

  fun testFindsUsagesOfTableAlias() {
    myFixture.configureByText(SqlDelightFileType, """
      |CREATE TABLE test (
      |  stuff TEXT NOT NULL
      |);
      |
      |someSelect:
      |SELECT test_alias.*
      |FROM test test_alias
      |WHERE test_alias.stuff = ?;
    """.trimMargin())
    val tableAlias = searchForElement<SqliteTableAlias>("test_alias") +
        searchForElement<SqliteTableName>("test_alias")
    assertThat(tableAlias).hasSize(3)

    assertThat(myFixture.findUsages(tableAlias.first())).containsExactly(*tableAlias.drop(1).map {
      UsageInfo(it)
    }.toTypedArray())
  }

  fun testFindsUsagesCommonTableName() {
    myFixture.configureByText(SqlDelightFileType, """
      |CREATE TABLE test (
      |  stuff TEXT NOT NULL
      |);
      |
      |someSelect:
      |WITH test_alias AS (
      |  SELECT *
      |  FROM test
      |)
      |SELECT test_alias.*
      |FROM test_alias
      |WHERE test_alias.stuff = ?;
    """.trimMargin())
    val tableName = searchForElement<SqliteTableName>("test_alias")
    assertThat(tableName).hasSize(4)

    assertThat(myFixture.findUsages(tableName.first())).containsExactly(*tableName.map {
      UsageInfo(it)
    }.toTypedArray())
  }

  fun testFindsUsagesCommonTableColumnAlias() {
    myFixture.configureByText(SqlDelightFileType, """
      |CREATE TABLE test (
      |  stuff TEXT NOT NULL
      |);
      |
      |someSelect:
      |WITH test_alias (stuff_alias) AS (
      |  SELECT *
      |  FROM test
      |)
      |SELECT stuff_alias
      |FROM test_alias
      |WHERE test_alias.stuff_alias = ?;
    """.trimMargin())
    val columnAlias = searchForElement<SqliteColumnAlias>("stuff_alias") +
        searchForElement<SqliteColumnName>("stuff_alias")
    assertThat(columnAlias).hasSize(3)

    assertThat(myFixture.findUsages(columnAlias.first())).containsExactly(*columnAlias.drop(1).map {
      UsageInfo(it)
    }.toTypedArray())
  }
}