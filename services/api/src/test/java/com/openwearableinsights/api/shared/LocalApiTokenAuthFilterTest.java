package com.openwearableinsights.api.shared;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link LocalApiTokenAuthFilter} and {@link LocalApiTokenStore},
 * covering the local single-user token contract from ADR-0008: header name,
 * 401 JSON error shape, path scoping to /api/v1/**, and token persistence
 * across store instances (simulating a restart).
 */
class LocalApiTokenAuthFilterTest {

    @Test
    void rejectsApiRequestMissingHeader(@TempDir Path tmp) throws Exception {
        LocalApiTokenStore store = new LocalApiTokenStore(tmp.resolve("token").toString());
        LocalApiTokenAuthFilter filter = new LocalApiTokenAuthFilter(store);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/readiness/latest");
        request.setServletPath("/api/v1/readiness/latest");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).isEqualTo("{\"error\": \"Missing or invalid X-Local-Api-Token header\"}");
        assertThat(response.getContentType()).contains("application/json");
    }

    @Test
    void rejectsApiRequestWithWrongToken(@TempDir Path tmp) throws Exception {
        LocalApiTokenStore store = new LocalApiTokenStore(tmp.resolve("token").toString());
        LocalApiTokenAuthFilter filter = new LocalApiTokenAuthFilter(store);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/readiness/latest");
        request.setServletPath("/api/v1/readiness/latest");
        request.addHeader("X-Local-Api-Token", "definitely-not-the-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void allowsApiRequestWithCorrectToken(@TempDir Path tmp) throws Exception {
        LocalApiTokenStore store = new LocalApiTokenStore(tmp.resolve("token").toString());
        LocalApiTokenAuthFilter filter = new LocalApiTokenAuthFilter(store);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/readiness/latest");
        request.setServletPath("/api/v1/readiness/latest");
        request.addHeader("X-Local-Api-Token", store.token());
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull(); // chain was invoked, i.e. request passed through
        assertThat(response.getStatus()).isEqualTo(200); // MockHttpServletResponse defaults to 200 when untouched
    }

    @Test
    void leavesActuatorHealthAndSwaggerOpenWithoutHeader(@TempDir Path tmp) throws Exception {
        LocalApiTokenStore store = new LocalApiTokenStore(tmp.resolve("token").toString());
        LocalApiTokenAuthFilter filter = new LocalApiTokenAuthFilter(store);

        for (String path : new String[] {"/actuator/health", "/actuator/info", "/v3/api-docs", "/swagger-ui.html"}) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
            request.setServletPath(path);
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(request, response, chain);

            assertThat(chain.getRequest()).as("path %s should pass through untouched", path).isNotNull();
            assertThat(response.getStatus()).as("path %s should not be short-circuited with 401", path).isNotEqualTo(401);
        }
    }

    @Test
    void tokenSurvivesAcrossStoreInstancesLikeARestart(@TempDir Path tmp) {
        String path = tmp.resolve("token").toString();

        LocalApiTokenStore firstBoot = new LocalApiTokenStore(path);
        LocalApiTokenStore secondBoot = new LocalApiTokenStore(path);

        assertThat(secondBoot.token()).isEqualTo(firstBoot.token());
    }
}
