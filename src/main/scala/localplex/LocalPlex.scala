package localplex

import com.twitter.finagle.{Filter, Http, Service}
import com.twitter.finagle.http.{Request, Response, Status}
import com.twitter.server.TwitterServer
import com.twitter.util.{Await, Future}


object LocalPlex extends TwitterServer {

  case class PlexFilter(db: Downstream.Db) extends Filter[Request,Response,PlexServiceRequest,Response] {

    def apply(req: Request, svc: Service[PlexServiceRequest, Response]): Future[Response] = {

      val maybeDs: Option[Downstream] = req.headerMap
        .get("Host")
        .map(_.takeWhile(_ != ':'))
        .flatMap(db.get)

      maybeDs match {
        case Some(ds) => svc(PlexServiceRequest(req, ds))
        case None =>
          Future.value(Response(Status.BadRequest))
      }
    }
  }

  case class PlexServiceRequest(req: Request, ds: Downstream)

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

  def main() {

    val db = Downstream.loadHostFile("/etc/hosts")

    val port = sys.env.getOrElse("PORT", "1024").toLong

    db.values.foreach {
      case Downstream.HttpServer(host, proxy) => log.info(s"http://$host:$port pointing to $proxy")
    }

    val service = PlexFilter(db) andThen PlexService

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
