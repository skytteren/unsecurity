package io.unsecurity.auth
package auth0
package oidc

import java.net.URI

import cats.effect.Sync
import com.auth0.jwk.JwkProvider
import io.unsecurity.Unsecurity
import io.unsecurity.hlinx.HLinx.HLinx
import io.unsecurity.hlinx.ParamConverter
import no.scalabin.http4s.directives.Directive
import org.http4s.{Method, Response, ResponseCookie}
import org.slf4j.Logger
import shapeless.HNil

class Auth0OidcUnsecurity[F[_]: Sync, U](baseUrl: HLinx[HNil],
                                         val sc: Auth0OidcSecurityContext[F, U],
                                         jwkProvider: JwkProvider,
                                         val log: Logger)
    extends Unsecurity[F, OidcAuthenticatedUser, U] {

  implicit val uriParamConverter = ParamConverter.createSimple(s => new URI(s))

  val login =
    unsecure(
      Endpoint(
        "oidc login endpoint",
        Method.GET,
        baseUrl / "login",
        Produces.Directive.EmptyBody
      )
    ).run(
      _ =>
        for {
          returnToUrlParam      <- queryParamAs[URI]("next")
          _                     = log.trace("/login returnToUrlParam: {}", returnToUrlParam)
          auth0CallbackUrlParam <- queryParamAs[URI]("auth0Callback")
          _                     = log.trace("/login auth0CallbackUrlParam: {}", auth0CallbackUrlParam)
          state                 <- sc.randomString(32).successF
          callbackUrl           = auth0CallbackUrlParam.getOrElse(sc.authConfig.defaultAuth0CallbackUrl)
          returnToUrl           = returnToUrlParam.getOrElse(sc.authConfig.defaultReturnToUrl)
          stateCookie <- sc.Cookies
                          .createStateCookie(secureCookie = callbackUrl.getScheme.equalsIgnoreCase("https"))
                          .successF
          _        <- sc.sessionStore.storeState(stateCookie.content, state, returnToUrl, callbackUrl).successF
          auth0Url = sc.createAuth0Url(state, callbackUrl)
          _        <- break(Redirect(auth0Url).addCookie(stateCookie))
        } yield {
          ()
      }
    )

  val callback =
    unsecure(
      Endpoint(
        "oidc callback endpoint",
        method = Method.GET,
        path = baseUrl / "callback",
        Produces.Directive.EmptyBody
      )
    ).run(
      _ =>
        for {
          stateCookie   <- cookie(sc.Cookies.Keys.STATE)
          stateParam    <- requiredQueryParam("state")
          xForwardedFor <- request.header("X-Forwarded-For")
          state         <- sc.validateState(stateCookie, stateParam, xForwardedFor.map(_.value))
          _             = log.trace("/callback state cookie matches state param")
          codeParam     <- requiredQueryParam("code")
          _             = log.trace("/callback callbackUrl: {}", state.callbackUrl)
          token         <- sc.fetchTokenFromAuth0(codeParam, state.callbackUrl)
          oidcUser      <- sc.verifyTokenAndGetOidcUser(token, jwkProvider)
          _             = log.trace("/callback userProfile: {}", oidcUser)
          sessionCookie <- sc.Cookies
                            .createSessionCookie(
                              secureCookie = state.callbackUrl.getScheme.equalsIgnoreCase("https")
                            )
                            .successF
          _ <- sc.sessionStore.storeSession(sessionCookie.content, oidcUser).successF
          returnToUrl = if (sc.isReturnUrlWhitelisted(state.returnToUrl)) {
            state.returnToUrl
          } else {
            log.warn(
              s"/callback returnToUrl (${state.returnToUrl}) not whitelisted; falling back to ${sc.authConfig.defaultReturnToUrl}")
            sc.authConfig.defaultReturnToUrl
          }
          xsrf <- sc.Cookies.createXsrfCookie(secureCookie = returnToUrl.getScheme.equalsIgnoreCase("https")).successF
          _    <- sc.sessionStore.removeState(stateCookie.content).successF
          _ <- break(
                Redirect(returnToUrl)
                  .addCookie(ResponseCookie(name = sc.Cookies.Keys.STATE, content = "", maxAge = Option(-1)))
                  .addCookie(sessionCookie)
                  .addCookie(xsrf)
              )
        } yield {
          ()
      }
    )

  val logout =
    secure(
      Endpoint(
        "oidc logout endpoint",
        Method.POST,
        baseUrl / "logout",
        Produces.Directive.EmptyBody
      )
    ).noAuthorization
      .run(
        _ =>
          for {
            cookie <- sc.sessionCookie
            _      <- sc.sessionStore.removeSession(cookie.content).successF
            _ <- break(
                  Redirect(sc.authConfig.afterLogoutUrl)
                    .addCookie(
                      ResponseCookie(name = sc.Cookies.Keys.K_SESSION_ID, content = "", maxAge = Option(-1))
                    )
                    .addCookie(
                      ResponseCookie(name = sc.Cookies.Keys.XSRF, content = "", maxAge = Option(-1), httpOnly = false)
                    )
                )
          } yield {
            ()
        }
      )

  val endpoints = List(login, callback, logout)

  def break(response: => Response[F]): Directive[F, Response[F]] = {
    Directive.error[F, Response[F]](response)
  }

}
