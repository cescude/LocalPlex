package localplex

import com.twitter.finagle.{Filter, Http, Service}
import com.twitter.finagle.http.{Request, Response, Status}
import com.twitter.server.TwitterServer
import com.twitter.util.{Await, Future}


object LocalPlex extends TwitterServer {

  case class UnknownHost(staticPage: String) extends Service[Request,Response] {

    val cannedResponse = {
      val resp = Response(Status.Ok)
      resp.contentString = staticPage
      resp
    }

    def apply(req: Request): Future[Response] =
      Future.value(cannedResponse)
  }

  case class PlexFilter(db: Endpoint.Db, missingService: UnknownHost) extends Filter[Request,Response,PlexServiceRequest,Response] {

    def apply(req: Request, svc: Service[PlexServiceRequest, Response]): Future[Response] = {

      val maybeEndpoint: Option[Endpoint] = req.headerMap
        .get("Host")
        .map(_.takeWhile(_ != ':'))
        .flatMap(db.get)

      maybeEndpoint match {
        case Some(ds) =>
          svc(PlexServiceRequest(req, ds))

        case None =>
          missingService(req)
      }
    }
  }

  case class PlexServiceRequest(req: Request, ds: Endpoint)

  case object PlexService extends Service[PlexServiceRequest,Response] {
    def apply(psr: PlexServiceRequest): Future[Response] = {
      val req = psr.req
      val ds  = psr.ds

      req.headerMap.remove("Connection")

      log.info(s"${ds.name} => $req")

      ds.conn(req)
        .onSuccess(resp => log.info(s"${ds.name} => $resp"))
        .onFailure(ex => log.info(s"${ds.name} => $ex"))
    }
  }

  def missingServicePage(db: Endpoint.Db, localPort: Long): String = {
    s"""<html>
       |<head></head>
       |<body>
       |  ${
      db.values.map {
        case Endpoint(host, proxyaddr) =>
          s"""<a href="http://$host:$localPort">$host ($proxyaddr)</a>"""
      }.mkString("<p>", "</p><p>", "</p>")
    }
       |</body>
       |</html>""".stripMargin
  }

  def main() {

    val db = Endpoint.loadHostFile("/etc/hosts")

    val port = sys.env.getOrElse("PORT", "1024").toLong

    db.values.foreach {
      case Endpoint(host, proxy) => log.info(s"http://$host:$port pointing to $proxy")
    }

    val unknownHostService = UnknownHost(missingServicePage(db, port))

    val service = PlexFilter(db, unknownHostService) andThen PlexService

    val server = Http.server
      .withStatsReceiver(statsReceiver)
      .withLabel("local-plex-router")
      .serve(s":$port", service)

    onExit {
      server.close()
    }

    Await.ready(server)
  }
}
