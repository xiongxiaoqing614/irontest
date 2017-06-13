package io.irontest.resources;

import io.irontest.core.assertion.AssertionVerifier;
import io.irontest.core.assertion.AssertionVerifierFactory;
import io.irontest.db.EndpointDAO;
import io.irontest.models.TestResult;
import io.irontest.models.assertion.Assertion;
import io.irontest.models.assertion.AssertionVerificationRequest;
import io.irontest.models.assertion.AssertionVerificationResult;
import io.irontest.models.endpoint.Endpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.List;

/**
 * A pseudo JSON RPC service for hosting action oriented browser-server interactions.
 * It is not based on JSON-RPC spec, nor is it a REST resource.
 * Created by Zheng on 27/07/2015.
 */
@Path("/jsonservice") @Produces({ MediaType.APPLICATION_JSON })
public class JSONService {
    private static final Logger LOGGER = LoggerFactory.getLogger(JSONService.class);
    private AssertionVerifierFactory assertionVerifierFactory;
    private EndpointDAO endpointDAO;

    public JSONService(AssertionVerifierFactory assertionVerifierFactory, EndpointDAO endpointDAO) {
        this.assertionVerifierFactory = assertionVerifierFactory;
        this.endpointDAO = endpointDAO;
    }

    /**
     * This is a stateless operation, i.e. not persisting anything in database.
     * @param assertionVerificationRequest
     * @return
     * @throws InterruptedException
     */
    @POST @Path("verifyassertion")
    public AssertionVerificationResult verifyAssertion(AssertionVerificationRequest assertionVerificationRequest)
            throws InterruptedException {
        Thread.sleep(100);  //  workaround for Chrome 44 to 48's 'Failed to load response data' problem (no such problem in Chrome 49)
        Assertion assertion = assertionVerificationRequest.getAssertion();
        AssertionVerifier assertionVerifier = assertionVerifierFactory.create(assertion.getType());
        AssertionVerificationResult result = null;
        try {
            result = assertionVerifier.verify(assertion, assertionVerificationRequest.getInput());
        } catch (Exception e) {
            LOGGER.error("Failed to verify assertion", e);
            result = new AssertionVerificationResult();
            result.setResult(TestResult.FAILED);
            result.setError(e.getMessage());
        }
        return result;
    }

    @GET @Path("findManagedEndpointsByType")
    public List<Endpoint> findManagedEndpointsByType(@QueryParam("type") String endpointType) {
        return endpointDAO.findManagedEndpointsByType(endpointType);
    }
}
