package localplex

import com.twitter.finagle.{Filter, Service}
import com.twitter.finagle.http.{Request, Response, Status}
import com.twitter.util.Future
import localplex.LocalPlex.log

object Proxy {

  case class Args(requestPayload: Request, endpoint: Endpoint)
  case class Result(responsePayload: Response)

  def makeForwardingService(db: Endpoint.Db, missingMessage: String): Service[Request,Response] =
    HttpCodec(db, missingMessage) andThen ForwardingService


  case class HttpCodec(db: Endpoint.Db, missingMessage: String)
    extends Filter[Request,Response,Args,Result] {

    def apply(req: Request, svc: Service[Args, Result]) = {

      val maybeEndpoint: Option[Endpoint] = req.headerMap
        .get("Host")
        .map(_.takeWhile(_ != ':'))
        .flatMap(db.get)

      maybeEndpoint match {
        case Some(ds) =>
          svc(Args(req, ds))
            .map(_.responsePayload)

        case None =>
          val resp = Response(Status.Ok)
          resp.contentType = "text/plain"
          resp.contentString = missingMessage
          Future.value(resp)
      }
    }
  }


  case object ForwardingService extends Service[Args, Result] {

    def apply(pxr: Args) = {

      val payload = pxr.requestPayload
      val endpoint = pxr.endpoint

      // Drop the connection header to avoid messing with finagle's logic
      payload.headerMap.remove("Connection")

      // Update the host header to set the "true" hostname
      payload.headerMap.set("Host", endpoint.uri.getHost)

      log.info(s"${endpoint.name} => $payload")

      endpoint.conn(payload)
        .onSuccess(resp => log.info(s"${endpoint.name} => $resp"))
        .onFailure(ex => log.info(s"${endpoint.name} => $ex"))
        .map(Result.apply)
    }
  }
}
