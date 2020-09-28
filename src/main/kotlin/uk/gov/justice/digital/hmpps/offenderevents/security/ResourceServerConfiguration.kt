@file:Suppress("DEPRECATION")

package uk.gov.justice.digital.hmpps.offenderevents.security


import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter
import org.springframework.security.config.http.SessionCreationPolicy

@Configuration
@EnableWebSecurity
class ResourceServerConfiguration : WebSecurityConfigurerAdapter() {
  override fun configure(http: HttpSecurity) {
    http
        .sessionManagement()
        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
        .and().csrf().disable()
        .authorizeRequests { auth ->
          auth.antMatchers("/webjars/**", "/favicon.ico", "/csrf",
              "/health/**", "/info", "/prometheus/**",
              "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
              .permitAll().anyRequest().authenticated()
        }.oauth2ResourceServer().jwt().jwtAuthenticationConverter(AuthAwareTokenConverter())
  }
}