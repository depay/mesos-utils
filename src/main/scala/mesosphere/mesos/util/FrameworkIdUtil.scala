package mesosphere.mesos.util

import org.apache.mesos.state.State
import mesosphere.util.BackToTheFuture
import org.apache.mesos.Protos.{ FrameworkInfo => FrameworkInfoProto, FrameworkID => FrameworkIDProto }
import com.google.protobuf.InvalidProtocolBufferException
import java.util.logging.{ Level, Logger }
import scala.util.{ Failure, Success }
import scala.concurrent.{ Future, Await, ExecutionContext }
import scala.concurrent.duration.Duration
import mesosphere.util.BackToTheFuture.Timeout

/**
  * Utility class for keeping track of a framework ID in Mesos state.
  *
  * @param state State implementation
  * @param key The key to store the framework ID under
  */

class FrameworkIdUtil(val state: State, val key: String = "frameworkId") {

  private val log = Logger.getLogger(getClass.getName)

  import BackToTheFuture.futureToFutureOption

  def fetch(implicit ec: ExecutionContext, timeout: Timeout): Option[FrameworkIDProto] = {
    val f: Future[Option[FrameworkIDProto]] = state.fetch(key).map {
      case Some(variable) if variable.value().length > 0 =>
        try {
          val frameworkId = FrameworkIDProto.parseFrom(variable.value())
          Some(frameworkId)
        }
        catch {
          case e: InvalidProtocolBufferException =>
            log.warning("Failed to parse framework ID")
            None
        }
      case _ => None
    }
    Await.result(f, timeout.duration)
  }

  def store(frameworkId: FrameworkIDProto)(implicit ec: ExecutionContext, timeout: Timeout) {
    state.fetch(key).map {
      case Some(oldVariable) =>
        val newVariable = oldVariable.mutate(frameworkId.toByteArray)
        state.store(newVariable).onComplete {
          case Success(_) =>
            log.info("Stored framework ID '%s'".format(frameworkId.getValue))
          case Failure(t) =>
            log.log(Level.WARNING, "Failed to store framework ID", t)
        }
      case _ => log.warning("Fetch framework ID returned nothing")
    }
  }

  def setIdIfExists(frameworkInfo: FrameworkInfoProto.Builder)(implicit ec: ExecutionContext, timeout: Timeout) {
    fetch match {
      case Some(id) =>
        log.info("Setting framework ID to %s".format(id.getValue))
        frameworkInfo.setId(id)
      case None =>
        log.info("No previous framework ID found")
    }
  }
}
