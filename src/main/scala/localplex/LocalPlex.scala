package localplex

import com.twitter.finagle.http.service.RoutingService
import com.twitter.finagle.{Filter, Http, Service, SimpleFilter}
import com.twitter.finagle.http.{Request, Response, Status}
import com.twitter.server.TwitterServer
import com.twitter.util.{Await, Future}

sealed trait Downstream { def conn: Service[Request, Response] }
object Downstream {
  case class HttpServer(name: String, conn: Service[Request, Response]) extends Downstream
  //case class StaticFiles(host: String, path: String) extends Downstream
}

case class Db() {
  var hosts: Map[String,Downstream] = Map.empty

  def get(downstreamName: String): Option[Downstream] =
    hosts.get(downstreamName)

  def add(downstreamName: String, downstreamAddr: String) = {

    val newConn = Http.client.newService(downstreamAddr, downstreamName)
    val downstreamServer = Downstream.HttpServer(downstreamName, newConn)

    hosts = hosts + (downstreamName -> downstreamServer)
  }

  def drop(downstreamName: String) = {
    hosts = hosts - downstreamName
  }
}

object LocalPlex extends TwitterServer {

  case class PlexFilter(db: Db) extends Filter[Request,Response,PlexServiceRequest,Response] {

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

  case class PlexService(db: Db) extends Service[PlexServiceRequest,Response] {
    def apply(psr: PlexServiceRequest): Future[Response] = {
      val req = psr.req
      val ds  = psr.ds

      req.headerMap.remove("Connection")

      ds.conn(req)
    }
  }

  def main() {

    val db = Db()

    val api = PlexFilter(db) andThen PlexService(db)

    val addSvc = Service.mk[Request,Response] { req: Request =>
      val hostname: Option[String] = req.params.get("host")
      val addr: Option[String]     = req.params.get("addr")

      (hostname, addr) match {
        case (Some(h), Some(a)) =>
          db.add(h, a)
          Future.value(Response(Status.Ok))

        case _ =>
          Future.value(Response(Status.BadRequest))
      }
    }

    val dropSvc = Service.mk[Request,Response] { req: Request =>
      req.params.get("host") match {
        case Some(h) =>
          db.drop(h)
          Future.value(Response(Status.Ok))

        case _ =>
          Future.value(Response(Status.BadRequest))
      }
    }

    val router = RoutingService.byPath[Request] {
      case "/lp/add"  => addSvc
      case "/lp/drop" => dropSvc
      case _          => api
    }

    val server = Http.server
      .withStatsReceiver(statsReceiver)
      .withLabel("local-plex-router")
      .serve(s":${sys.env.getOrElse("PORT", "3333")}", router)

    onExit {
      server.close()
    }

    Await.ready(server)
  }
}
