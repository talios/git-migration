package com.theoryinpractise.gitmigration

import org.scalatest.FunSuite
import scala.io.Source
import java.io.File
import scala.slick.session.Database
import scala.slick.jdbc.StaticQuery
import scala.Predef._

class GitMigrationSuite extends FunSuite {


  test("a git repository contains migrations") {


    val repoDir: String = "/Users/amrk/Dropbox/migrationdb/"
    val migrationRepo = new GitMigrationRepository(repoDir)
    val migrations = migrationRepo.findMigrationsBetween()

    assert(migrations.size > 0)

  }


  test("chunked source reads in chunks") {

    val source =
      """
        |This is one line;
        |-- comment
        |This is another
        |line of text;
        |-- comment
        |
      """.stripMargin.trim


    var count: Int = 0

    processSourceInChunkedLines(Source.fromBytes(source.getBytes)) {
      (lineNumber, line) =>
        assert(!line.contains("--"))
        println(s"$lineNumber: $line")
        count = count + 1
    }

    assert(count === 2)
  }

  test("database should migrate") {

    import Database.threadLocalSession

    lazy val database = Database.forURL("jdbc:postgresql:testmigration")

    val repoDir: String = "/Users/amrk/Dropbox/migrationdb/"
    val migrationRepo = new GitMigrationRepository(repoDir)
    val migrations = migrationRepo.findMigrationsBetween()

    migrations foreach {
      migration =>
        println(s"  => ${migration.sha} :: ${migration.shortMessage}")
        database withSession {
          migration.files foreach {
            fileName =>
              val file = new File(repoDir, fileName)
              print(s"    => ${file.getPath}..")
              processSourceInChunkedLines(Source.fromFile(file)) {
                (lineMumber, line) =>
                  try {
                    StaticQuery.updateNA(line).execute()
                    print(".")
                  } catch {
                    case e: Throwable => {
                      println("")
                      println(s"       Unable to migrate with ${file.getPath}:$lineMumber from migration ${migration.sha}: ${e.getMessage}")
                      println(line)
                    }
                  }
              }
              println(". done.")
          }
        }
    }
  }


}
