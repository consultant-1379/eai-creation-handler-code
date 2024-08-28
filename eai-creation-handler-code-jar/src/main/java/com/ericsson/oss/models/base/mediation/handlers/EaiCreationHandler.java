/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2012
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/
package com.ericsson.oss.models.base.mediation.handlers;

import static com.ericsson.oss.mediation.engine.api.MediationEngineConstants.PTR_FDN;
import static com.ericsson.oss.mediation.engine.api.MediationEngineConstants.REMOTE_HOST_ATTR;
import static com.ericsson.oss.mediation.engine.api.MediationEngineConstants.REMOTE_PORT_ATTR;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import javax.ejb.CreateException;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.rmi.PortableRemoteObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ericsson.oss.itpf.common.config.Configuration;
import com.ericsson.oss.itpf.common.event.handler.EventHandlerContext;
import com.ericsson.oss.itpf.common.event.handler.EventInputHandler;
import com.ericsson.oss.itpf.common.event.handler.annotation.EventHandler;
import com.ericsson.oss.itpf.common.event.handler.exception.EventHandlerException;
import com.ericsson.oss.itpf.datalayer.dps.remote.RemoteDataPersistenceService;
import com.ericsson.oss.itpf.datalayer.dps.remote.RemoteDataPersistenceServiceHome;
import com.ericsson.oss.itpf.datalayer.dps.remote.dto.ManagedObjectDto;
import com.ericsson.oss.itpf.datalayer.dps.remote.exception.DataPersistenceServiceRemoteException;

/**
 * This handler is executed as part of a CM mediation add node boot strap synchronous flow. This means the DPS TX is still active and has not been
 * committed yet. Any runtime exception thrown here will roll back the DPS TX and the flow will fail. This handler does the following:<br>
 * <br>
 * 1. Does a look up of the remote DPS <br>
 * 2. Creates a new <code>EntityAddressInfo</code> PO object <br>
 * 3. Sets this as the <code>EntityAddressInfo</code> on the MO
 *
 */
@EventHandler(contextName = "")
public class EaiCreationHandler implements EventInputHandler {

    private static final Logger LOG = LoggerFactory.getLogger(EaiCreationHandler.class);
    private static final String CONFIG_ATTR_MESSAGE = "The config attributes are: {} ";
    private static final String CONFIG_ATTR_NULL_OR_EMPTY_MSG = "This Config attribute was Null or Empty: ";
    private static final String REMOTE_DPS_LOOKUP_SUCCESS_MSG = "Remote DPS lookup successfull";
    private static final String REMOTE_DPS_LOOKUP_ATTEMPT_MSG = "Performing remote JNDI lookup with {}";
    private static final String REMOTE_DPS_LOOKUP_FAILURE_MSG = "Error doing jndi lookup of Remote Data Persistence service";
    private static final String EAI_CREATION_FAILURE_MSG = "Unable to create EntityAddressInfo PO: {}";
    private static final String EAI_CREATION_SUCCESS_MSG = "Successfully created EAI with Id: {}";
    private static final String EAI_SET_ON_MO_OK_MSG = "Successfully set EAI on MO: {}";
    private static final String EAT_SET_ON_MO_FAIL_MSG = "Failed to set EAI on MO: ";
    private static final String GET_MO_FAILURE_MSG = "No MO exists with FDN: {} ";
    private static final String LIVE_BUCKET = null;
    private static final String NE_TYPE_ATTR = "neType";
    private static final String PLATFORM_TYPE_ATTR = "platformType";
    private static final String TARGET_NAMESPACE_KEYS = "targetNamespaceKeys";
    // TODO This should not be hard-coded to make it CDS compliant
    private final static String EAI_NAMESPACE = "MEDIATION";
    // TODO This should not be hard-coded to make it CDS compliant
    private final static String EAI_TYPE = "EntityAddressingInformation";
    // TODO This should not be hard-coded to make it CDS compliant
    private final static String VERSION = "1.0.0";
    private InitialContext jndiContext;
    private String remoteHost;
    private String remotePort;
    private String objectOfInvocationFdn;
    private String neType;
    private String platformType;

    private RemoteDataPersistenceService remoteDps;

    /*
     * (non-Javadoc)
     *
     * @see com.ericsson.oss.itpf.common.event.handler.EventHandler#init(com.ericsson .oss.itpf.common.event.handler.EventHandlerContext)
     */
    @Override
    public void init(final EventHandlerContext ctx) {
        extractParameters(ctx.getEventHandlerConfiguration());
    }

    /**
     * Creates an EntityAddressInfo PO and then sets that EntityAddressInfo on the MO.
     */
    @Override
    public void onEvent(final Object inputEvent) {
        final String className =this.getClass().getName();
        LOG.debug("onEvent called: {}", className);
        lookupRemoteDps();
        final long eaiId=createEntityAddressInfo();
        setEntityAddressInfo(eaiId);
        LOG.debug("onEvent finished: {}", className);
    }

    /*
     * (non-Javadoc)
     *
     * @see com.ericsson.oss.itpf.common.Destroyable#destroy()
     */
    @Override
    public void destroy() {
    }

    /**
     * Does look up of the DPS using its remote interface (specifically designed for bootstrap use case as its all done within same TX and TX has not
     * been committed yet)
     */
    private void lookupRemoteDps() {
        try {
            if (jndiContext == null) {
                jndiContext = new InitialContext();
            }
            final String lookupString = "corbaname:iiop:" + remoteHost + ":" + remotePort + "#" + RemoteDataPersistenceServiceHome.REMOTE_LOOKUP_NAME;
            LOG.debug(REMOTE_DPS_LOOKUP_ATTEMPT_MSG, lookupString);
            final Object iiopObject = jndiContext.lookup(lookupString);
            final RemoteDataPersistenceServiceHome ejbHome = (RemoteDataPersistenceServiceHome) PortableRemoteObject.narrow(iiopObject,
                    RemoteDataPersistenceServiceHome.class);
            remoteDps = ejbHome.create();
        } catch (NamingException | RemoteException | CreateException e) {
            LOG.error(REMOTE_DPS_LOOKUP_FAILURE_MSG, e);
            throw new EventHandlerException(REMOTE_DPS_LOOKUP_FAILURE_MSG, e);
        }
        LOG.debug(REMOTE_DPS_LOOKUP_SUCCESS_MSG);
    }

    private long createEntityAddressInfo() {
        try {
            final Collection<String> targetNamespaceKeys = createTargetNamespaceKeys();
            final Map<String, Object> eaiAttributes = new HashMap<String, Object>();
            eaiAttributes.put(TARGET_NAMESPACE_KEYS, targetNamespaceKeys);
            final long eaiPoId = remoteDps.createPo(LIVE_BUCKET, EAI_NAMESPACE, EAI_TYPE, VERSION, eaiAttributes);
            LOG.debug(EAI_CREATION_SUCCESS_MSG, eaiPoId);
            return eaiPoId;
        } catch (RemoteException | DataPersistenceServiceRemoteException e) {
            LOG.error(EAI_CREATION_FAILURE_MSG, new Object[] { EAI_NAMESPACE, EAI_TYPE, VERSION });
            throw new EventHandlerException(EAI_CREATION_FAILURE_MSG, e);
        }
    }

    private Collection<String> createTargetNamespaceKeys() {
        final Collection<String> targetNamespaceKeys = new ArrayList<String>();
        targetNamespaceKeys.add(neType);
        targetNamespaceKeys.add(platformType);
        return targetNamespaceKeys;
    }

    private void setEntityAddressInfo(final long eaiId) {
        try {
            final ManagedObjectDto objectOfInvocation = remoteDps.getMo(LIVE_BUCKET, objectOfInvocationFdn);
            if (objectOfInvocation == null) {
                LOG.error(GET_MO_FAILURE_MSG, objectOfInvocationFdn);
                throw new EventHandlerException(GET_MO_FAILURE_MSG + objectOfInvocationFdn);
            }
            final Long objectOfInvocationId = objectOfInvocation.getPoId();

            remoteDps.setEntityAddressInfo(LIVE_BUCKET, objectOfInvocationId, eaiId);

            LOG.debug(EAI_SET_ON_MO_OK_MSG, objectOfInvocationFdn);
        } catch (RemoteException | DataPersistenceServiceRemoteException e) {
            LOG.error(EAT_SET_ON_MO_FAIL_MSG + objectOfInvocationFdn, e.getMessage());
            throw new EventHandlerException(EAT_SET_ON_MO_FAIL_MSG + objectOfInvocationFdn, e);
        }
    }

    private void extractParameters(final Configuration config) {
        LOG.debug(CONFIG_ATTR_MESSAGE, config.getAllProperties());
        remoteHost = config.getStringProperty(REMOTE_HOST_ATTR);
        verifyNotEmpty(remoteHost, REMOTE_HOST_ATTR);
        remotePort = config.getStringProperty(REMOTE_PORT_ATTR);
        verifyNotEmpty(remotePort, REMOTE_PORT_ATTR);
        this.objectOfInvocationFdn = config.getStringProperty(PTR_FDN);
        verifyNotEmpty(objectOfInvocationFdn, PTR_FDN);
        this.neType = config.getStringProperty(NE_TYPE_ATTR);
        verifyNotEmpty(neType, NE_TYPE_ATTR);
        this.platformType = config.getStringProperty(PLATFORM_TYPE_ATTR);
        verifyNotEmpty(platformType, PLATFORM_TYPE_ATTR);
    }

    /*
     * Throws exception if value is empty. No need to check if it's null as this is already done when Configuration.getStringProperty is called.
     */
    private void verifyNotEmpty(final String value, final String propName) {
        if (value.isEmpty()) {
            throw new EventHandlerException(CONFIG_ATTR_NULL_OR_EMPTY_MSG + propName);
        }
    }

}