package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.server.ConfigurableWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.stereotype.Component;
import java.util.stream.Stream;


@SpringBootApplication
public class DemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(DemoApplication.class, args);
	}

	@Component
	public static class ServerPortCustomizer
			implements WebServerFactoryCustomizer<ConfigurableWebServerFactory> {
		@Override
		public void customize(ConfigurableWebServerFactory factory) {
			factory.setPort(3000);
		}
	}
	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http.oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
		http.oauth2Login(Customizer.withDefaults());

		return http
				.authorizeHttpRequests(c -> c.requestMatchers("/error").permitAll()
						.requestMatchers("/manager.html").hasRole("MANAGER")
						.anyRequest().authenticated())
				.build();
	}

	@Bean
	public JwtAuthenticationConverter jwtAuthenticationConverter() {
		var converter = new JwtAuthenticationConverter();
		var jwtGrantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();
		converter.setPrincipalClaimName("preferred_username");
		converter.setJwtGrantedAuthoritiesConverter(jwt -> {
			var authorities = jwtGrantedAuthoritiesConverter.convert(jwt);
			var roles = jwt.getClaimAsStringList("spring_sec_roles");

			return Stream.concat(authorities.stream(),
							roles.stream()
									.filter(role -> role.startsWith("ROLE_"))
									.map(SimpleGrantedAuthority::new)
									.map(GrantedAuthority.class::cast))
					.toList();
		});

		return converter;
	}

	@Bean
	public OAuth2UserService<OidcUserRequest, OidcUser> oAuth2UserService() {
		var oidcUserService = new OidcUserService();
		return userRequest -> {
			var oidcUser = oidcUserService.loadUser(userRequest);
			var roles = oidcUser.getClaimAsStringList("spring_sec_roles");
			var authorities = Stream.concat(oidcUser.getAuthorities().stream(),
							roles.stream()
									.filter(role -> role.startsWith("ROLE_"))
									.map(SimpleGrantedAuthority::new)
									.map(GrantedAuthority.class::cast))
					.toList();

			return new DefaultOidcUser(authorities, oidcUser.getIdToken(), oidcUser.getUserInfo());
		};
	}
}