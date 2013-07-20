package com.theoryinpractise.gitmigration

import org.rogach.scallop.Scallop
import java.io.File
import scala.io.Source
import scala.slick.jdbc.StaticQuery
import scala.slick.session.{Session, Database}

/**
 * Created by amrk on 20/07/13.
 */
object GitMigration extends App {


  val opts = Scallop(args)
    .version("0.0.1-SNAPSHOT (c) 2013 Mark Derricutt")
    .banner( """Usage: git migrate [OPTION]...
               |Yeh baby - migrate that db.""".stripMargin)
    .footer("\nFor all other tricks, consult the documentation!")
    .opt[String]("jdbc", descr = "JDBC URL of database to migrate", required = true, short = 'J')
    .opt[String]("base", descr = "SHA1 hash to migration from", default = () => None, short = 'B')
    .opt[String]("head", descr = "SHA1 hash to migration upto", default = () => Some("HEAD"), short = 'H')
    .opt[String]("repo", descr = "Git repository to migrate from", default = () => Some("."), short = 'R')
    .opt[Boolean]("files", descr = "Show migration files being executed", noshort = true )

  try {
    opts.verify
  } catch {
    case e: Throwable => {
      println(e.getMessage)
      println(opts.help)
      System.exit(-1)
    }
  }

  val repoDir = opts.get[String]("repo").get
  val showFiles = opts.get[Boolean]("files").get

  val migrations = {
    val migrationRepo = new GitMigrationRepository(repoDir)
    val baseSha = opts.get[String]("base")
    val currentSha = opts.get[String]("head")
    migrationRepo.findMigrationsBetween(base = baseSha, current = currentSha)
  }
  
  val database = Database.forURL(opts.get[String]("jdbc").get)

  try {
    migrations foreach {
      migration =>
        println(s"  => ${migration.sha} :: ${migration.shortMessage}")
        database withSession {
          implicit session: Session =>
            session withTransaction {
              migration.files foreach {
                fileName =>
                  val file = new File(repoDir, fileName)
                  if (showFiles) print(s"    => ${file.getPath}..")
                  processSourceInChunkedLines(Source.fromFile(file)) {
                    (lineMumber, line) =>
                      try {
                        StaticQuery.updateNA(line).execute()
                        if (showFiles) print(".")
                      } catch {
                        case e: Throwable => {
                          if (showFiles) println("")
                          println(s"       Unable to migrate with ${file.getPath}:$lineMumber from migration ${migration.sha}: ${e.getMessage}")
                          println(line)
                          throw e
                        }
                      }
                  }
                  if (showFiles) println(". done.")
              }
            }
        }
    }
  } catch {
    case _: Throwable => System.exit(-1)
  }

}

