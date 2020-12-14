package wth.model.http

import org.http4s.{EntityDecoder, EntityEncoder}
import zio.Task

trait CirceEntitySerDes[T] extends EntitySerDes[T] {
  implicit def encoder: EntityEncoder[Task, T]
  implicit def decoder: EntityDecoder[Task, T]
}
