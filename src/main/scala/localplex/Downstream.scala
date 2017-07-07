package localplex

import com.twitter.finagle.{Http, Service}
import com.twitter.finagle.http.{Request, Response}

sealed trait Downstream {
  def name: String
  def conn: Service[Request, Response]
}
object Downstream {
  case class HttpServer(name: String, proxy: String) extends Downstream {
    val conn: Service[Request, Response] = {
      Http.client.newService(proxy, name)
    }
  }
  //case class StaticFiles(host: String, path: String) extends Downstream


  type Db = Map[String, Downstream]

  /** SYNOPSIS
    *
    *  Update your /etc/hosts file to configure.  Only lines matching the
    *  following pattern will be included.
    *
    *  {{{
    *  # Format is:
    *  #   127.0.0.1 <hostname> # LocalPlex:<remoteaddr>:<remoteport>
    *
    *  # Define three HTTP servers to proxy traffic through:
    *
    *  127.0.0.1 rails-dev       # LocalPlex:localhost:3000
    *  127.0.0.1 some-service    # LocalPlex:localhost:8888
    *  127.0.0.1 localplex-admin # LocalPlex:localhost:9990
    *
    *  # You can comment the beginning of the line to omit the configured proxy,
    *  as well.
    *
    *  # 127.0.0.1 offline-service # LocalPlex:localhost:1234
    *  }}}
    *
    */
  def loadHostFile(hostFile: String): Db = {

    // TODO: Allow other addresses beyond binding the local 127.0.0.1?
    //       Seems moderately safer to keep this as is.
    val httpServerPattern = raw"\s*127.0.0.1\s*(\S+)\s*#\s*LocalPlex:(\S+:\d+)\s*".r

    scala.io.Source.fromFile(hostFile).getLines
      .collect({
        case httpServerPattern(hostname, remoteaddr) =>
          (hostname -> Downstream.HttpServer(hostname, remoteaddr))
      })
      .toMap
  }
}
