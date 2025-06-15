package com.frever.cmsAuthorization.utils;

import com.frever.cmsAuthorization.services.CmsAuthorizationService;
import io.quarkus.logging.Log;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.security.PermitAll;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.DynamicFeature;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.FeatureContext;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jose4j.jwa.AlgorithmConstraints;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jwt.MalformedClaimException;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.jose4j.jwt.consumer.JwtConsumer;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;
import org.jose4j.jwt.consumer.JwtContext;

@Provider
@ApplicationScoped
public class AuthTokenVerificationDynamicFeature implements DynamicFeature {
    @Inject
    CmsAuthorizationService cmsAuthorizationService;
    @ConfigProperty(name = "cms.authorization.dev1.jwt.public.key")
    String dev1PublicKey;
    @ConfigProperty(name = "cms.authorization.dev.jwt.public.key")
    String devPublicKey;
    @ConfigProperty(name = "cms.authorization.content-test.jwt.public.key")
    String contentTestPublicKey;
    @ConfigProperty(name = "cms.authorization.content-stage.jwt.public.key")
    String contentStagePublicKey;
    @ConfigProperty(name = "cms.authorization.content-prod.jwt.public.key")
    String contentProdPublicKey;
    @ConfigProperty(name = "cms.authorization.ixia-prod.jwt.public.key")
    String ixiaProdPublicKey;

    static final Map<RequestEnv, PublicKey> publicKeys = new HashMap<>();

    static final Set<String> adminEndpoints = new HashSet<>();

    boolean runningInTest = Boolean.parseBoolean(System.getProperty("runningTests", "false"));

    static final List<String> CONSULTANT_EMAILS = List.of(
        "benjamin.bjork@sentorsecurity.com",
        "joakim.a.johansson@sentorsecurity.com",
        "laban.skollermark@sentorsecurity.com",
        "erik@modulai.io",
        "system@frever-system.com",
        "ts21@sentorlab.se",
        "dev55@ff.se"
    );

    @PostConstruct
    void postConstruct() {
        if (runningInTest) {
            return;
        }
        try {
            KeyFactory kf = KeyFactory.getInstance("RSA");
            publicKeys.put(RequestEnv.DEV, calculatePublicKey(devPublicKey, kf));
            publicKeys.put(RequestEnv.DEV_1, calculatePublicKey(dev1PublicKey, kf));
            publicKeys.put(RequestEnv.CONTENT_TEST, calculatePublicKey(contentTestPublicKey, kf));
            publicKeys.put(RequestEnv.CONTENT_STAGE, calculatePublicKey(contentStagePublicKey, kf));
            publicKeys.put(RequestEnv.CONTENT_PROD, calculatePublicKey(contentProdPublicKey, kf));
            publicKeys.put(RequestEnv.IXIA_PROD, calculatePublicKey(ixiaProdPublicKey, kf));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    static PublicKey calculatePublicKey(String publicKey, KeyFactory kf) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(publicKey);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
            return kf.generatePublic(spec);
        } catch (InvalidKeySpecException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void configure(ResourceInfo resourceInfo, FeatureContext context) {
        if (runningInTest) {
            return;
        }
        var m = resourceInfo.getResourceMethod();
        var permitAll = m.getAnnotation(PermitAll.class);
        if (permitAll == null) {
            context.register(new AuthTokenVerificationRequestFilter(cmsAuthorizationService));
        }
        var adminEndpoint = m.getAnnotation(AdminEndpoint.class);
        if (adminEndpoint != null) {
            var resourceClassPath = "";
            var resourceMethodPath = "";
            var resourceClass = resourceInfo.getResourceClass();
            var resourcePath = resourceClass.getAnnotation(Path.class);
            if (resourcePath != null) {
                resourceClassPath = resourcePath.value();
            }
            var methodPath = m.getAnnotation(Path.class);
            if (methodPath != null) {
                resourceMethodPath = methodPath.value();
            }
            var adminPath = resourceClassPath + (resourceMethodPath.indexOf("/") == 0 ? "" : "/") + resourceMethodPath;
            adminEndpoints.add(adminPath);
        }
    }

    static class AuthTokenVerificationRequestFilter implements ContainerRequestFilter {
        private final CmsAuthorizationService cmsAuthorizationService;

        public AuthTokenVerificationRequestFilter(CmsAuthorizationService cmsAuthorizationService) {
            this.cmsAuthorizationService = cmsAuthorizationService;
        }

        PublicKey getPublicKey(RequestEnv env) {
            return publicKeys.get(env);
        }

        @Override
        public void filter(ContainerRequestContext requestContext) {
            RequestEnv env = RequestEnv.fromEnv(requestContext.getHeaderString("request-env"));
            if (env == null) {
                requestContext.abortWith(Response.status(Response.Status.BAD_REQUEST).build());
                return;
            }
            String authToken = requestContext.getHeaderString("AuthToken");
            if (authToken == null || authToken.isEmpty()) {
                requestContext.abortWith(Response.status(Response.Status.UNAUTHORIZED).build());
                return;
            }
            try {
                JwtConsumer jwtConsumer = new JwtConsumerBuilder().setRequireExpirationTime()
                    .setAllowedClockSkewInSeconds(30)
                    .setRequireSubject()
                    .setExpectedIssuer("http://auth-service")
                    .setExpectedAudience("friends_factory.creators_api")
                    .setVerificationKey(getPublicKey(env))
                    .setJwsAlgorithmConstraints(
                        AlgorithmConstraints.ConstraintType.PERMIT,
                        AlgorithmIdentifiers.RSA_USING_SHA256,
                        AlgorithmIdentifiers.RSA_USING_SHA384,
                        AlgorithmIdentifiers.RSA_USING_SHA512
                    )
                    .build();
                JwtContext jwt = jwtConsumer.process(authToken);
                Log.info("ReqEnv: " + env + " ,jwt claims: " + jwt.getJwtClaims());
                String email = jwt.getJwtClaims().getClaimValue("email", String.class);
                if (!isEmployee(email) && !isConsultant(email)) {
                    requestContext.abortWith(Response.status(Response.Status.FORBIDDEN).build());
                }
                String path = requestContext.getUriInfo().getPath();
                if (adminEndpoints.contains(path)) {
                    if (!cmsAuthorizationService.isUserAdmin(email)) {
                        requestContext.abortWith(Response.status(Response.Status.FORBIDDEN).build());
                    }
                }
            } catch (InvalidJwtException | MalformedClaimException e) {
                Log.info("Invalid JWT: " + e);
                requestContext.abortWith(Response.status(Response.Status.FORBIDDEN).build());
            }
        }

        private static boolean isEmployee(String email) {
            return email.endsWith("@frever.com");
        }

        private static boolean isConsultant(String email) {
            return CONSULTANT_EMAILS.contains(email);
        }
    }
}
