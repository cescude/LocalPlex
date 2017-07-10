package localplex

import com.twitter.finagle.{Http, Service}
import com.twitter.finagle.http.{Request, Response}
import java.net.URI

case class Endpoint(name: String, uri: URI) {

  // Extract finagle-friendly destination address, and also figure out if we
  // need to enable SSL

  val (destAddr: String, endpointSSL: Boolean) = {
    val remoteaddr = uri.getHost

    val remoteport = (uri.getPort, uri.getScheme) match {
      case (port, _) if port > -1 => port
      case (_, "http")            => 80
      case (_, "https")           => 443
      case otherwise =>
        throw new Exception(s"Unable to determine port number to connect to for $uri")
    }

    (s"$remoteaddr:$remoteport", uri.getScheme == "https")
  }

  // The Endpoint service we'll be forwarding messages through

  val conn: Service[Request, Response] = {

    val client = endpointSSL match {
      case true  => Http.client.withTlsWithoutValidation
      case false => Http.client
    }

    client.newService(destAddr, name)
  }
}

object Endpoint {

  type Db = Map[String, Endpoint]

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
    *  127.0.0.1 rails-dev       # LocalPlex:http://localhost:3000
    *  127.0.0.1 some-service    # LocalPlex:https://localhost:8888 # <-- note https works fine, too
    *  127.0.0.1 localplex-admin # LocalPlex:http://localhost:9990
    *
    *  # You can comment the beginning of the line to omit the configured proxy,
    *  as well.
    *
    *  # 127.0.0.1 offline-service # LocalPlex:http://localhost:1234
    *  }}}
    *
    */
  def loadEndpointsFromHostsFile(hostFile: String): Db = {

    val httpServerPattern = raw"\s*127.0.0.1\s*(\S+)\s*#\s*LocalPlex:(http\S+)\s*".r

    scala.io.Source.fromFile(hostFile).getLines
      .collect({
        case httpServerPattern(hostname, rawuri) =>
          hostname -> Endpoint(hostname, URI.create(rawuri))
      })
      .toMap
  }
}
