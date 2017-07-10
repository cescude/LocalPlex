package localplex

import com.twitter.finagle.{Filter, Http, Service}
import com.twitter.finagle.http.{Request, Response, Status}
import com.twitter.server.TwitterServer
import com.twitter.util.{Await, Future}


object LocalPlex extends TwitterServer {



  def endpointListing(db: Endpoint.Db, localPort: Long): String = {
    db.values
      .map({
        case Endpoint(host, uri) => s"$host:$localPort [ $uri ]"
      })
      .mkString("\n")
  }

  def main() {

    val db = Endpoint.loadEndpointsFromHostsFile("/etc/hosts")

    val port = sys.env.getOrElse("PORT", "1024").toLong

    db.values.foreach {
      case Endpoint(host, proxy) =>
        log.info(s"$host:$port pointing to $proxy")
    }

    val server = Http.server
      .withStatsReceiver(statsReceiver)
      .withLabel("local-plex-router")
      .serve(s"localhost:$port", Proxy.makeForwardingService(db, endpointListing(db, port)))

    onExit {
      server.close()
    }

    Await.ready(server)
  }
}
