package utils

import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.{FileSystem, Path}
import org.apache.hadoop.io.IOUtils
import org.apache.hadoop.io.compress.CompressionCodec
import org.apache.spark.rdd.RDD

import java.io.{IOException, OutputStream}
import scala.collection.mutable.ListBuffer
import scala.io.Source
import scala.sys.process.{BasicIO, ProcessIO, _}
import scala.util.Try

object CustomOperators {

  // Custom operators for Iterator[String]
  implicit class IteratorStringOperators(lines: Iterator[String]) {

    // InputStream for ProcessIO
    val in: OutputStream => Unit = (in: OutputStream) => {
      lines.foreach(line => in.write((line + "\n").getBytes))
      in.close()
    }

    // Do exactly the same job as RDD's pipe function for Iterator[String]
    def pipeCmd(cmd: String): Iterator[String] = {
      val output = ListBuffer[String]()
      val io = new ProcessIO(
        in,
        out => {Source.fromInputStream(out).getLines.foreach(output += _)},
        err => {Source.fromInputStream(err).getLines.foreach(System.err.println(_:String))}
      )

      cmd.run(io).exitValue

      // Return output value from stdout
      output.toIterator
    }

    def runCmd(cmd: String): Int = {
      cmd.run(BasicIO.standard(in)).exitValue
    }
  }


  // Custom operators for RDD
  implicit class RDDOperators[T](val rdd: RDD[T]) extends AnyVal {

    def saveAsSingleTextFile(path: String): Unit = saveAsSingleTextFileInternal(path, None)

    def saveAsSingleTextFile(path: String, codec: Class[_ <: CompressionCodec]): Unit =
      saveAsSingleTextFileInternal(path, Some(codec))

    def filterDivisor(f: T => Boolean): (RDD[T], RDD[T]) = {
      val passes = rdd.filter(f)
      val fails = rdd.filter(e => !f(e))
      (passes, fails)
    }

    /*
      We manually implement this function instead of using FileUtil.copyMerge
      because it is deprecated from version above 3 of Hadoop. You can find out
      more information about this change at https://issues.apache.org/jira/browse/HADOOP-11392
     */
    private def copyMerge(
                           srcFS: FileSystem, srcDir: Path,
                           dstFS: FileSystem, dstFile: Path,
                           deleteSource: Boolean, conf: Configuration
                         ): Boolean = {
      if (dstFS.exists(dstFile))
        throw new IOException(s"Target $dstFile already exists")
      // Source path is expected to be a directory:
      if (srcFS.getFileStatus(srcDir).isDirectory) {

        val outputFile = dstFS.create(dstFile)
        Try {
          srcFS.listStatus(srcDir)
            .sortBy(_.getPath.getName)
            .collect {
              case status if status.isFile =>
                val inputFile = srcFS.open(status.getPath)
                Try(IOUtils.copyBytes(inputFile, outputFile, conf, false))
                inputFile.close()
            }
        }
        outputFile.close()

        if (deleteSource) srcFS.delete(srcDir, true) else true
      } else false
    }

    private def saveAsSingleTextFileInternal(
                                              path: String, codec: Option[Class[_ <: CompressionCodec]]
                                            ): Unit = {
      // The interface with hdfs:
      val hdfs = FileSystem.get(rdd.sparkContext.hadoopConfiguration)

      // Classic saveAsTextFile in a temporary folder:
      hdfs.delete(new Path(s"$path.tmp"), true)
      codec match {
        case Some(codec) => rdd.saveAsTextFile(s"$path.tmp", codec)
        case None        => rdd.saveAsTextFile(s"$path.tmp")
      }

      // Merge the folder of resulting part-xxxxx into one file:
      hdfs.delete(new Path(path), true)
      copyMerge(
        hdfs, new Path(s"$path.tmp"),
        hdfs, new Path(path),
        true, rdd.sparkContext.hadoopConfiguration
      )

      hdfs.delete(new Path(s"$path.tmp"), true)
    }
  }
}
