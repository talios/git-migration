package com.theoryinpractise

import scala.collection.JavaConversions._
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.diff.{RawTextComparator, DiffFormatter}
import org.eclipse.jgit.util.io.DisabledOutputStream
import org.eclipse.jgit.revwalk.{RevCommit, RevWalk}
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import java.io.File
import org.eclipse.jgit.api.Git
import scala.io.Source
import org.eclipse.jgit.treewalk.TreeWalk

/**
 * Created by amrk on 20/07/13.
 */
package object gitmigration {

  case class MigrationSet(sha: String, shortMessage: String, insert: Option[String] = None, files: List[String])

  type SHA1 = Option[String]

  implicit class GitWalker(repository: Repository) {
    def walk[T](f: (RevWalk => T)) = {
      val walk = new RevWalk(repository)
      val result = f(walk)
      walk.dispose()
      result
    }
  }

  case class ChunkedSource(startingLine: Int, endingLine: Int, content: String)

  def processSourceInChunkedLines(source: Source)(callback: ((Int, String) => Unit)): Unit = {

    def isLineValid(line: String) = !line.startsWith("--") && !"".equals(line.trim())
    def hasMatchingQuotes(content: String) = "(\\\\$\\\\$|'|\\\")\"".r.findAllMatchIn(content).size % 2 == 0
    def isComplete(content: String) = hasMatchingQuotes(content) && content.trim().endsWith(";")

    source.getLines().foldLeft(ChunkedSource(1, 1, "")) {
      (chunk, line) =>

        val currentLineNumber = chunk.endingLine + 1
        val currentLineIsValid = isLineValid(line)
        val currentChunkContent = chunk.content + line + "\n"
        val currentChunkContentIsValid = isLineValid(currentChunkContent)

        val currentChunk = (currentChunkContentIsValid, currentLineIsValid) match {
          case (_, true)      => ChunkedSource(chunk.startingLine, currentLineNumber, currentChunkContent)
          case (true, false)  => ChunkedSource(chunk.startingLine, currentLineNumber, chunk.content)
          case (false, false) => ChunkedSource(currentLineNumber, currentLineNumber, chunk.content)
        }

        isComplete(currentChunk.content) match {
          case true => {
            callback(currentChunk.startingLine, currentChunk.content)
            ChunkedSource(currentLineNumber, currentLineNumber, "")
          }
          case _    => currentChunk
        }

    }
  }

  class GitMigrationRepository(baseDir: String) {

    val repositoryDir = if (baseDir.endsWith("/")) baseDir else s"$baseDir/"
    val builder = new FileRepositoryBuilder()
    val repository = builder.setGitDir(new File(s"$repositoryDir.git")).readEnvironment().findGitDir().build()
    val git = new Git(repository)
    val diffFormatter = mkDiffFormatter(repository)

    private def mkDiffFormatter(repository: Repository) = {
      val df = new DiffFormatter(DisabledOutputStream.INSTANCE)
      df.setRepository(repository)
      df.setDiffComparator(RawTextComparator.DEFAULT)
      df.setDetectRenames(true)
      df
    }

    private def migrationIn(commit: RevCommit): MigrationSet = repository.walk {
      w =>

        val filesInCommit = if (commit.getParentCount == 0) {
          // No parent - so walk the tree returning files in tree
          val walk = new TreeWalk(repository)
          val files = scala.collection.mutable.MutableList[String]()
          walk.addTree(commit.getTree);
          while (walk.next()) {
            files += walk.getPathString
          }

          files.toList

        } else {

          val parent = w.parseCommit(commit.getParent(0).getId)
          val diffs = diffFormatter.scan(parent.getTree, commit.getTree)
          diffs.map(_.getNewPath).toList
        }

        MigrationSet(sha = commit.getName, shortMessage = commit.getShortMessage, files = filesInCommit)

    }


    def findMigrationsBetween(base: SHA1 = None, current: SHA1 = Some("HEAD")) = {

      def resolveCommit(sha: String) = repository.walk(_.parseCommit(repository.resolve(sha)))
      def findBaseCommit = git.log().all().call().toList.last
      def findHeadCommit = resolveCommit("HEAD")

      val currentCommit = current map resolveCommit getOrElse findHeadCommit

      val commits = base match {
        case None    => {
          val baseCommit = findBaseCommit
          baseCommit +: git.log().addRange(baseCommit, currentCommit).call().toList.reverse
        }
        case Some(c) => git.log().addRange(resolveCommit(c), currentCommit).call().toList.reverse
      }

      val migrations = commits map migrationIn filterNot {
        m => m.shortMessage.startsWith("Merge")
      }

      migrations
    }
  }


}
