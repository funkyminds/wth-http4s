package wth.service.http

import io.circe.Decoder
import org.http4s._
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.headers.Accept
import org.http4s.util.CaseInsensitiveString
import wth.model.http.CirceEntitySerDes
import wth.service.http.HttpClient._
import zio._
import zio.interop.catz._

case class Http4s(client: Client[Task]) extends HttpClient.Service[CirceEntitySerDes] with Http4sClientDsl[Task] {
  override def get[T](uri: String, paths: Seq[String], parameters: Map[String, String], headers: Map[String, String])(
    serDes: CirceEntitySerDes[T]
  ): Task[T] = {
    import serDes._
    val full: Uri = createUri(uri, paths, parameters)

    val request: Request[Task] = Request(
      Method.GET,
      full,
      headers = Http4s.parseHeaders(headers)
    )

    client.expect[T](request)
  }

  private def createUri[T](uri: String, paths: Seq[String], parameters: Map[String, String]): Uri = {
    val baseUri: Uri = paths.foldLeft(Uri.unsafeFromString(uri))(_ / _) // maybe not use unsafe
    baseUri.withQueryParams(parameters)
  }

  override def post[T](
    payload: T,
    uri: String,
    paths: Seq[String],
    parameters: Map[String, String],
    headers: Map[String, String]
  )(serDes: CirceEntitySerDes[T]): Task[Boolean] = {
    import serDes._
    val full: Uri = createUri(uri, paths, parameters)

    val request: Request[Task] = Request(
      Method.POST,
      full,
      headers = Http4s.parseHeaders(headers)
    ).withEntity(payload)

    client.successful(request)
  }
}

object Http4s {
  type Http4s = Has[Client[Task]]

  def http4s: URLayer[Http4s, HttpClient[CirceEntitySerDes]] =
    ZLayer.fromService[Client[Task], HttpClient.Service[CirceEntitySerDes]] { http4sClient =>
      Http4s(http4sClient)
    }

  def makeManagedHttpClient: UIO[TaskManaged[Client[Task]]] =
    ZIO.runtime[Any].map { implicit rts =>
      val exec = rts.platform.executor.asEC
      BlazeClientBuilder
        .apply[Task](exec)
        .resource
        .toManaged
    }

  private def parseHeaders(rawHeaders: Map[String, String]): Headers = {
    val headers = rawHeaders
      .collect {
        case ("X-Secret", secret) => Header.Raw(CaseInsensitiveString("X-Secret"), secret)
        case ("Accept", toAccept) => Accept.parse(toAccept).getOrElse(throw new RuntimeException) // TODO: change to ZIO
      }
      .toSet[Header]

    Headers.of(headers.toSeq: _*)
  }
}
