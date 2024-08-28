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
package com.ericsson.nms.mediation.component.flow.handler.eaicreation;

import static com.ericsson.oss.mediation.engine.api.MediationEngineConstants.PTR_FDN;
import static com.ericsson.oss.mediation.engine.api.MediationEngineConstants.REMOTE_HOST_ATTR;
import static com.ericsson.oss.mediation.engine.api.MediationEngineConstants.REMOTE_PORT_ATTR;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.rmi.RemoteException;
import java.util.Map;

import javax.naming.InitialContext;
import javax.rmi.PortableRemoteObject;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import com.ericsson.oss.itpf.common.config.Configuration;
import com.ericsson.oss.itpf.common.event.handler.EventHandlerContext;
import com.ericsson.oss.itpf.common.event.handler.exception.EventHandlerException;
import com.ericsson.oss.itpf.datalayer.dps.remote.RemoteDataPersistenceService;
import com.ericsson.oss.itpf.datalayer.dps.remote.RemoteDataPersistenceServiceHome;
import com.ericsson.oss.itpf.datalayer.dps.remote.dto.ManagedObjectDto;
import com.ericsson.oss.itpf.datalayer.dps.remote.exception.DataPersistenceServiceRemoteException;
import com.ericsson.oss.models.base.mediation.handlers.EaiCreationHandler;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ PortableRemoteObject.class })
public class EaiCreationHandlerTest {

    private final static long EAI_PO_ID = 1111;
    private final static long OOI_PO_ID = 2222;
    private final static String MF_FDN = "Me=test,MeC=test,ENodeBFunction=1";
    private final static String REMOTE_HOST_ATTR_VALUE = "127.0.0.1";
    private final static String REMOTE_PORT_ATTR_VALUE = "3528";
    private final static String EAI_NAMESPACE = "MEDIATION";
    private final static String EAI_TYPE = "EntityAddressingInformation";
    private final static String VERSION = "1.0.0";
    private final static String LIVE_BUCKET = null;
    private final static String EMPTY = "";
    private static final String NE_TYPE_ATTR = "neType";
    private static final String PLATFORM_TYPE_ATTR = "platformType";

    private static final String NE_TYPE = "NeType";
    private static final String PLATFORM_TYPE = "PlatformType";
    private final String LOOKUP_STRING = "corbaname:iiop:" + REMOTE_HOST_ATTR_VALUE + ":" + REMOTE_PORT_ATTR_VALUE + "#"
            + RemoteDataPersistenceServiceHome.REMOTE_LOOKUP_NAME;
    @Mock
    private RemoteDataPersistenceService remoteDpsMock;
    @Mock
    private RemoteDataPersistenceServiceHome remoteDpsHomeMock;
    @Mock
    private InitialContext initialContextMock;
    @Mock
    private EventHandlerContext eventHandlerContextMock;
    @Mock
    private Configuration configurationMock;
    @InjectMocks
    private EaiCreationHandler eaiCreateHandler;


    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(eventHandlerContextMock.getEventHandlerConfiguration()).thenReturn(configurationMock);
        when(configurationMock.getStringProperty(REMOTE_HOST_ATTR)).thenReturn("127.0.0.1");
        when(configurationMock.getStringProperty(REMOTE_PORT_ATTR)).thenReturn("3528");
        when(configurationMock.getStringProperty(PTR_FDN)).thenReturn(MF_FDN);
        when(configurationMock.getStringProperty(NE_TYPE_ATTR)).thenReturn(NE_TYPE);
        when(configurationMock.getStringProperty(PLATFORM_TYPE_ATTR)).thenReturn(PLATFORM_TYPE);
        when(remoteDpsMock.createPo(eq(LIVE_BUCKET), eq(EAI_NAMESPACE), eq(EAI_TYPE), eq(VERSION), any(Map.class))).thenReturn(EAI_PO_ID);
        setupGoodDps();
    }

    @Test(expected = EventHandlerException.class)
    public void test_HostEmpty() throws Exception {
        eaiCreateHandler.init(eventHandlerContextMock);
        when(configurationMock.getStringProperty(REMOTE_HOST_ATTR)).thenReturn(EMPTY);
        eaiCreateHandler.init(eventHandlerContextMock);
    }

    @Test(expected = EventHandlerException.class)
    public void test_PortEmpty() throws Exception {
        eaiCreateHandler.init(eventHandlerContextMock);
        when(configurationMock.getStringProperty(REMOTE_PORT_ATTR)).thenReturn(EMPTY);
        eaiCreateHandler.init(eventHandlerContextMock);
    }

    @Test(expected = EventHandlerException.class)
    public void test_FdnEmpty() throws Exception {
        eaiCreateHandler.init(eventHandlerContextMock);
        when(configurationMock.getStringProperty(PTR_FDN)).thenReturn(EMPTY);
        eaiCreateHandler.init(eventHandlerContextMock);
    }

    @Test(expected = EventHandlerException.class)
    public void test_NeTypeEmpty() throws Exception {
        eaiCreateHandler.init(eventHandlerContextMock);
        when(configurationMock.getStringProperty(NE_TYPE_ATTR)).thenReturn(EMPTY);
        eaiCreateHandler.init(eventHandlerContextMock);
    }

    @Test(expected = EventHandlerException.class)
    public void test_PlatformTypeEmpty() throws Exception {
        eaiCreateHandler.init(eventHandlerContextMock);
        when(configurationMock.getStringProperty(PLATFORM_TYPE_ATTR)).thenReturn(EMPTY);
        eaiCreateHandler.init(eventHandlerContextMock);
    }

    @SuppressWarnings("unchecked")
    @Test(expected = EventHandlerException.class)
    public void test_FailedToCreateEai() throws Exception {
        eaiCreateHandler.init(eventHandlerContextMock);
        when(remoteDpsMock.createPo(LIVE_BUCKET, EAI_NAMESPACE, EAI_TYPE, VERSION, null)).thenThrow(DataPersistenceServiceRemoteException.class);
        eaiCreateHandler.onEvent(null);
    }

    @Test(expected = EventHandlerException.class)
    public void test_MoNotFound() throws Exception {
        eaiCreateHandler.init(eventHandlerContextMock);
        when(remoteDpsMock.getMo(LIVE_BUCKET, MF_FDN)).thenReturn(null);
        eaiCreateHandler.onEvent(null);
    }

    @SuppressWarnings("unchecked")
    @Test(expected = EventHandlerException.class)
    public void test_GetMoWhenDpsException() throws Exception {
        eaiCreateHandler.init(eventHandlerContextMock);
        when(remoteDpsMock.getMo(LIVE_BUCKET, MF_FDN)).thenThrow(DataPersistenceServiceRemoteException.class);
        eaiCreateHandler.onEvent(null);
    }

    @Test(expected = EventHandlerException.class)
    public void test_RemoteDpsLookupFails() throws Exception {
        eaiCreateHandler.init(eventHandlerContextMock);
        setupBadDps();
        eaiCreateHandler.onEvent(null);
    }

    @Test
    public void test_EaiCreationSuccess() throws Exception {
        eaiCreateHandler.init(eventHandlerContextMock);
        final ManagedObjectDto mo = new ManagedObjectDto("namespace", "type", "version", OOI_PO_ID, null, "fdn", "name");
        when(remoteDpsMock.getMo(LIVE_BUCKET, MF_FDN)).thenReturn(mo);
        eaiCreateHandler.onEvent(null);
        verify(initialContextMock).lookup(LOOKUP_STRING);
        verify(remoteDpsMock).getMo(LIVE_BUCKET, MF_FDN);
        verify(remoteDpsMock).createPo(eq(LIVE_BUCKET), eq(EAI_NAMESPACE), eq(EAI_TYPE), eq(VERSION), any(Map.class));
        verify(remoteDpsMock).setEntityAddressInfo(LIVE_BUCKET, OOI_PO_ID, EAI_PO_ID);
    }

    private void setupGoodDps() throws Exception {
        PowerMockito.mockStatic(PortableRemoteObject.class);
        PowerMockito.when(PortableRemoteObject.narrow(any(Object.class), eq(RemoteDataPersistenceServiceHome.class))).thenReturn(remoteDpsHomeMock);
        when(initialContextMock.lookup(any(String.class))).thenReturn(new Object());
        when(remoteDpsHomeMock.create()).thenReturn(remoteDpsMock);
    }

    private void setupBadDps() throws Exception {
        PowerMockito.mockStatic(PortableRemoteObject.class);
        PowerMockito.when(PortableRemoteObject.narrow(any(Object.class), eq(RemoteDataPersistenceServiceHome.class))).thenReturn(remoteDpsHomeMock);
        when(initialContextMock.lookup(any(String.class))).thenReturn(new Object());
        when(remoteDpsHomeMock.create()).thenThrow(new RemoteException("Expected this!"));
    }
}