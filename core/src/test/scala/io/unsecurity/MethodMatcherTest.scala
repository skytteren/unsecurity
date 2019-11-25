package io.unsecurity

import cats.Id
import no.scalabin.http4s.directives.{Directive, Result}
import org.http4s.{Request, _}
import org.http4s.headers.Allow
import org.slf4j.{Logger, LoggerFactory}

class MethodMatcherTest extends UnsecurityTestSuite {
  val methodMatcher: AbstractMethodMatcher[Id] = new AbstractMethodMatcher[Id] {
    val log: Logger = LoggerFactory.getLogger("MethodMatcherTest")
  }

  val getRequest: Request[Id]    = Request[Id](method = Method.GET)
  val postRequest: Request[Id]   = Request[Id](method = Method.POST)
  val deleteRequest: Request[Id] = Request[Id](method = Method.DELETE)

  test("single method") {
    val methodMap: Map[Method, String] = Map(Method.GET -> "Only GET")

    val methodMatchDirective: Directive[Id, String] = methodMatcher.matchMethod(methodMap)

    methodMatchDirective.run(getRequest).where { case Result.Success("Only GET") => Ok }
    methodMatchDirective.run(deleteRequest).where {
      case Result.Error(r)
          if r.status == Status.MethodNotAllowed
            && r.headers.get(Allow).get.methods.length == 1
            && r.headers.get(Allow).get.methods.toList.contains(Method.GET) =>
        Ok
    }
  }

  test("multiple methods") {
    val methodMap: Map[Method, String] = Map(Method.GET -> "Get", Method.POST -> "Post", Method.PUT -> "Put")

    val methodMatchDirective: Directive[Id, String] = methodMatcher.matchMethod(methodMap)

    methodMatchDirective.run(getRequest).where { case Result.Success("Get")   => Ok }
    methodMatchDirective.run(postRequest).where { case Result.Success("Post") => Ok }
    methodMatchDirective.run(deleteRequest).where {
      case Result.Error(r)
          if r.status == Status.MethodNotAllowed
            && r.headers.get(Allow).get.methods.length == methodMap.values.size
            && r.headers.get(Allow).get.methods.toList.toSet == methodMap.keySet =>
        Ok
    }
  }
}
