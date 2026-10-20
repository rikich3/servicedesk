package com.servicedesk.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.servicedesk.support.EmbeddedPostgresConfig;
import com.servicedesk.support.TestData;
import java.net.HttpCookie;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

/**
 * Exercises the exact browser flow over a real HTTP port: the XSRF-TOKEN cookie is issued on
 * the first response, mutations without the mirrored header are rejected, and a successful
 * login yields an HttpOnly session cookie.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(EmbeddedPostgresConfig.class)
@ActiveProfiles("test")
class CsrfCookieFlowIT {

    @LocalServerPort int port;
    @Autowired TestData data;

    @Test
    void doubleSubmitCookieProtectsLogin() {
        data.reset();
        var user = data.user("browser");
        RestClient client = RestClient.builder().baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (req, res) -> { }).build();

        ResponseEntity<String> first = client.get().uri("/api/auth/me").retrieve().toEntity(String.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        String xsrf = cookieValue(first.getHeaders(), "XSRF-TOKEN");
        assertThat(xsrf).isNotBlank();
        assertThat(first.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).doesNotContain("HttpOnly");

        String body = "{\"email\":\"" + user.getEmail() + "\",\"password\":\"" + TestData.PASSWORD + "\"}";

        ResponseEntity<String> noHeader = client.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.COOKIE, "XSRF-TOKEN=" + xsrf)
                .body(body).retrieve().toEntity(String.class);
        assertThat(noHeader.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<Map> ok = client.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.COOKIE, "XSRF-TOKEN=" + xsrf)
                .header("X-XSRF-TOKEN", xsrf)
                .body(body).retrieve().toEntity(Map.class);
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ok.getBody()).containsEntry("email", user.getEmail());
        String sessionCookie = ok.getHeaders().get(HttpHeaders.SET_COOKIE).stream()
                .filter(c -> c.startsWith("JSESSIONID=")).findFirst().orElseThrow();
        assertThat(sessionCookie).contains("HttpOnly").contains("SameSite=Lax");

        String session = cookieValue(ok.getHeaders(), "JSESSIONID");
        ResponseEntity<Map> me = client.get().uri("/api/auth/me")
                .header(HttpHeaders.COOKIE, "JSESSIONID=" + session)
                .retrieve().toEntity(Map.class);
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(me.getBody()).containsEntry("fullName", user.getFullName());
    }

    private static String cookieValue(HttpHeaders headers, String name) {
        List<String> setCookies = headers.getOrEmpty(HttpHeaders.SET_COOKIE);
        return setCookies.stream()
                .flatMap(h -> HttpCookie.parse(h).stream())
                .filter(c -> c.getName().equals(name))
                .map(HttpCookie::getValue)
                .findFirst().orElse("");
    }
}
