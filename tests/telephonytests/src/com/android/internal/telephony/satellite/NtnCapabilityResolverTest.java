/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.internal.telephony.satellite;

import static android.telephony.AccessNetworkConstants.TRANSPORT_TYPE_WWAN;
import static android.telephony.NetworkRegistrationInfo.DOMAIN_PS;
import static android.telephony.NetworkRegistrationInfo.REGISTRATION_STATE_ROAMING;
import static android.telephony.NetworkRegistrationInfo.REGISTRATION_STATE_UNKNOWN;
import static android.telephony.NetworkRegistrationInfo.SERVICE_TYPE_DATA;
import static android.telephony.NetworkRegistrationInfo.SERVICE_TYPE_EMERGENCY;
import static android.telephony.NetworkRegistrationInfo.SERVICE_TYPE_MMS;
import static android.telephony.NetworkRegistrationInfo.SERVICE_TYPE_SMS;
import static android.telephony.TelephonyManager.NETWORK_TYPE_LTE;
import static android.telephony.TelephonyManager.NETWORK_TYPE_NR;
import static android.telephony.TelephonyManager.NETWORK_TYPE_UNKNOWN;
import static android.telephony.satellite.SatelliteManager.NT_RADIO_TECHNOLOGY_LTE_DTC;
import static android.telephony.satellite.SatelliteManager.NT_RADIO_TECHNOLOGY_NB_IOT_NTN;
import static android.telephony.satellite.SatelliteManager.NT_RADIO_TECHNOLOGY_NR_DTC;
import static android.telephony.satellite.SatelliteManager.NT_RADIO_TECHNOLOGY_NR_NTN;
import static android.telephony.satellite.SatelliteManager.NT_RADIO_TECHNOLOGY_UNKNOWN;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.annotation.NonNull;
import android.telephony.CellIdentity;
import android.telephony.CellIdentityGsm;
import android.telephony.NetworkRegistrationInfo;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.util.ArraySet;

import com.android.internal.telephony.TelephonyTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class NtnCapabilityResolverTest extends TelephonyTest {
    private static final String TAG = "NtnCapabilityResolverTest";
    private static final int SUB_ID = 0;
    private static final String VISITING_PLMN = "00102";
    private static final String SATELLITE_PLMN = "00103";
    private static final String EMPTY_PLMN = "";
    private static final Set<String> SATELLITE_PLMN_SET = Set.of(SATELLITE_PLMN);
    private static final List<Integer> CARRIER_ROAMING_SATELLITE_DEFAULT_SERVICES =
            List.of(SERVICE_TYPE_SMS, SERVICE_TYPE_MMS);
    private final int[] mSatelliteSupportedServices = {SERVICE_TYPE_SMS, SERVICE_TYPE_EMERGENCY};
    private final List<Integer> mSatelliteSupportedServiceList =
            Arrays.stream(mSatelliteSupportedServices).boxed().collect(Collectors.toList());

    @Mock private SatelliteController mMockSatelliteController;

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        MockitoAnnotations.initMocks(this);
        logd(TAG + " Setup!");

        replaceInstance(SatelliteController.class, "sInstance", null,
                mMockSatelliteController);
        doReturn(true).when(mMockSatelliteController).isSatelliteSupportedOnDevice();
        doReturn(SATELLITE_PLMN_SET)
                .when(mMockSatelliteController).getAllPlmnSet();
        doReturn(mSatelliteSupportedServiceList).when(mMockSatelliteController)
                .getSupportedSatelliteServicesForPlmn(SUB_ID, SATELLITE_PLMN);
        doReturn(CARRIER_ROAMING_SATELLITE_DEFAULT_SERVICES).when(mMockSatelliteController)
                .getSupportedSatelliteServicesForPlmn(SUB_ID, VISITING_PLMN);
    }

    @After
    public void tearDown() throws Exception {
        logd(TAG + " tearDown");
        super.tearDown();
    }

    @Test
    public void testResolveNTNCapability() {
        logd("Test resolving a satellite NetworkRegistrationInfo");
        NetworkRegistrationInfo satelliteNri = createNetworkRegistrationInfo(SATELLITE_PLMN);
        NetworkRegistrationInfo originalNri = new NetworkRegistrationInfo(satelliteNri);

        assertEquals(satelliteNri, originalNri);
        assertFalse(satelliteNri.isNonTerrestrialNetwork());
        assertFalse(Arrays.equals(mSatelliteSupportedServices,
                satelliteNri.getAvailableServices().stream()
                .mapToInt(Integer::intValue)
                .toArray()));
        doReturn(true).when(mMockSatelliteController)
                .isDtcSatelliteTechnologySupported(anyInt(), anyString());
        NtnCapabilityResolver.resolveNtnCapability(satelliteNri, SUB_ID);
        verify(mMockSatelliteController).getAllPlmnSet();
        assertNotEquals(satelliteNri, originalNri);
        assertTrue(satelliteNri.isNonTerrestrialNetwork());
        assertTrue(Arrays.equals(mSatelliteSupportedServices,
                satelliteNri.getAvailableServices().stream()
                        .mapToInt(Integer::intValue)
                        .toArray()));

        logd("Test resolving a non-satellite NetworkRegistrationInfo.");
        NetworkRegistrationInfo cellularNri = createNetworkRegistrationInfo(VISITING_PLMN);
        originalNri = new NetworkRegistrationInfo(cellularNri);

        assertEquals(cellularNri, originalNri);
        assertFalse(cellularNri.isNonTerrestrialNetwork());
        assertFalse(Arrays.equals(mSatelliteSupportedServices,
                cellularNri.getAvailableServices().stream()
                        .mapToInt(Integer::intValue)
                        .toArray()));
        NtnCapabilityResolver.resolveNtnCapability(cellularNri, SUB_ID);
        verify(mMockSatelliteController, times(2)).getAllPlmnSet();
        assertEquals(cellularNri, originalNri);
        assertFalse(cellularNri.isNonTerrestrialNetwork());
        assertFalse(Arrays.equals(mSatelliteSupportedServices,
                cellularNri.getAvailableServices().stream()
                        .mapToInt(Integer::intValue)
                        .toArray()));

        logd("Test resolving an empty-PLMN NetworkRegistrationInfo.");
        NetworkRegistrationInfo emptyPlmnNri =
                createNetworkRegistrationInfo(EMPTY_PLMN);
        originalNri = new NetworkRegistrationInfo(emptyPlmnNri);

        assertEquals(emptyPlmnNri, originalNri);
        assertFalse(emptyPlmnNri.isNonTerrestrialNetwork());
        assertFalse(Arrays.equals(mSatelliteSupportedServices,
                emptyPlmnNri.getAvailableServices().stream()
                        .mapToInt(Integer::intValue)
                        .toArray()));
        NtnCapabilityResolver.resolveNtnCapability(emptyPlmnNri, SUB_ID);
        verify(mMockSatelliteController, times(2)).getAllPlmnSet();
        assertEquals(emptyPlmnNri, originalNri);
        assertFalse(emptyPlmnNri.isNonTerrestrialNetwork());
        assertFalse(Arrays.equals(mSatelliteSupportedServices,
                emptyPlmnNri.getAvailableServices().stream()
                        .mapToInt(Integer::intValue)
                        .toArray()));
    }

    @Test
    public void testResolveNtnCapability_EmptyPlmn() {
        logd("testResolveNtnCapability_EmptyPlmn");
        NetworkRegistrationInfo emptyPlmnNri =
                createNetworkRegistrationInfo(EMPTY_PLMN, false, true);
        NetworkRegistrationInfo originalNri = new NetworkRegistrationInfo(emptyPlmnNri);

        NtnCapabilityResolver.resolveNtnCapability(emptyPlmnNri, SUB_ID);

        verify(mMockSatelliteController, never()).getAllPlmnSet();
        assertEquals("emptyPlmnNri should be the same as originalNri.",
                originalNri, emptyPlmnNri);
    }

    @Test
    public void testResolveNtnCapability_satelliteUnsupportedOnDevice_skipsResolution() {
        logd("testResolveNtnCapability_satelliteUnsupportedOnDevice_skipsResolution");
        doReturn(false).when(mMockSatelliteController).isSatelliteSupportedOnDevice();
        NetworkRegistrationInfo nri = createNetworkRegistrationInfo(SATELLITE_PLMN);
        NetworkRegistrationInfo originalNri = new NetworkRegistrationInfo(nri);

        NtnCapabilityResolver.resolveNtnCapability(nri, SUB_ID);

        verify(mMockSatelliteController, never()).getAllPlmnSet();
        assertEquals("nri should be the same as originalNri.", originalNri, nri);
        assertFalse(nri.isNonTerrestrialNetwork());
    }

    @Test
    public void testResolveNtnCapability_whenIsNtnEnabled() {
        logd("testResolveNtnCapability_whenIsNtnEnabled");
        doCallRealMethod().when(mMockSatelliteController)
                .isDtcSatelliteTechnologySupported(anyInt(), anyString());

        logd("case 1. matchedPlmn=false, DTC=false >> isNtn=true, services=[SMS,MMS]");
        NetworkRegistrationInfo unmatchedPlmnNri =
                createNetworkRegistrationInfo(VISITING_PLMN, true, true);
        NetworkRegistrationInfo originalNri = new NetworkRegistrationInfo(unmatchedPlmnNri);
        assertEquals(unmatchedPlmnNri, originalNri);
        assertTrue(unmatchedPlmnNri.isNonTerrestrialNetwork());
        assertNotEquals(new HashSet<>(mSatelliteSupportedServiceList),
                new HashSet<>(unmatchedPlmnNri.getAvailableServices()));
        assertNotEquals(new HashSet<>(CARRIER_ROAMING_SATELLITE_DEFAULT_SERVICES),
                new HashSet<>(unmatchedPlmnNri.getAvailableServices()));
        doReturn(List.of(NT_RADIO_TECHNOLOGY_NR_NTN)).when(mMockSatelliteController)
                .getSupportedSatelliteTechnologies(anyInt(), anyString());

        NtnCapabilityResolver.resolveNtnCapability(unmatchedPlmnNri, SUB_ID);
        assertTrue(unmatchedPlmnNri.isNonTerrestrialNetwork());
        assertNotEquals("unmatchedPlmnNri should not be the same with originalNri",
                unmatchedPlmnNri, originalNri);
        assertEquals(new HashSet<>(CARRIER_ROAMING_SATELLITE_DEFAULT_SERVICES),
                new HashSet<>(unmatchedPlmnNri.getAvailableServices()));

        logd("case 2. matchedPlmn=false, DTC=true >> isNtn=true, services=[SMS,MMS]");
        unmatchedPlmnNri =
                createNetworkRegistrationInfo(VISITING_PLMN, true, true);
        originalNri = new NetworkRegistrationInfo(unmatchedPlmnNri);
        assertEquals(unmatchedPlmnNri, originalNri);
        assertTrue(unmatchedPlmnNri.isNonTerrestrialNetwork());
        assertNotEquals(new HashSet<>(mSatelliteSupportedServiceList),
                new HashSet<>(unmatchedPlmnNri.getAvailableServices()));
        assertNotEquals(new HashSet<>(CARRIER_ROAMING_SATELLITE_DEFAULT_SERVICES),
                new HashSet<>(unmatchedPlmnNri.getAvailableServices()));
        doReturn(List.of(NT_RADIO_TECHNOLOGY_NR_DTC)).when(mMockSatelliteController)
                .getSupportedSatelliteTechnologies(anyInt(), anyString());

        NtnCapabilityResolver.resolveNtnCapability(unmatchedPlmnNri, SUB_ID);
        assertTrue(unmatchedPlmnNri.isNonTerrestrialNetwork());
        assertNotEquals("unmatchedPlmnNri should not be the same with originalNri",
                unmatchedPlmnNri, originalNri);
        assertEquals(new HashSet<>(CARRIER_ROAMING_SATELLITE_DEFAULT_SERVICES),
                new HashSet<>(unmatchedPlmnNri.getAvailableServices()));

        logd("case 3. matchedPlmn=true, DTC=false >> isNtn=true, services=[SMS,EMERGENCY]");
        NetworkRegistrationInfo matchedPlmnNri =
                createNetworkRegistrationInfo(SATELLITE_PLMN, true, true);
        originalNri = new NetworkRegistrationInfo(matchedPlmnNri);
        assertEquals(matchedPlmnNri, originalNri);
        assertTrue(matchedPlmnNri.isNonTerrestrialNetwork());
        assertNotEquals(new HashSet<>(mSatelliteSupportedServiceList),
                new HashSet<>(matchedPlmnNri.getAvailableServices()));
        assertNotEquals(new HashSet<>(CARRIER_ROAMING_SATELLITE_DEFAULT_SERVICES),
                new HashSet<>(matchedPlmnNri.getAvailableServices()));
        doReturn(List.of(NT_RADIO_TECHNOLOGY_NR_NTN)).when(mMockSatelliteController)
                .getSupportedSatelliteTechnologies(anyInt(), anyString());

        NtnCapabilityResolver.resolveNtnCapability(matchedPlmnNri, SUB_ID);
        assertNotEquals("matchedPlmnNri should not be the same with originalNri",
                matchedPlmnNri, originalNri);
        assertEquals(new HashSet<>(mSatelliteSupportedServiceList),
                new HashSet<>(matchedPlmnNri.getAvailableServices()));

        logd("case 4. matchedPlmn=true, DTC=true >> isNtn=true, services=[SMS,EMERGENCY]");
        matchedPlmnNri =
                createNetworkRegistrationInfo(SATELLITE_PLMN, true, true);
        originalNri = new NetworkRegistrationInfo(matchedPlmnNri);
        assertEquals(matchedPlmnNri, originalNri);
        assertTrue(matchedPlmnNri.isNonTerrestrialNetwork());
        assertNotEquals(new HashSet<>(mSatelliteSupportedServiceList),
                new HashSet<>(matchedPlmnNri.getAvailableServices()));
        assertNotEquals(new HashSet<>(CARRIER_ROAMING_SATELLITE_DEFAULT_SERVICES),
                new HashSet<>(matchedPlmnNri.getAvailableServices()));
        doReturn(List.of(NT_RADIO_TECHNOLOGY_NR_DTC)).when(mMockSatelliteController)
                .getSupportedSatelliteTechnologies(anyInt(), anyString());

        NtnCapabilityResolver.resolveNtnCapability(matchedPlmnNri, SUB_ID);
        assertNotEquals("matchedPlmnNri should not be the same with originalNri",
                matchedPlmnNri, originalNri);
        assertEquals(new HashSet<>(mSatelliteSupportedServiceList),
                new HashSet<>(matchedPlmnNri.getAvailableServices()));
    }

    @Test
    public void testResolveSatelliteTechnology() {
        logd("testResolveSatelliteTechnology");
        doCallRealMethod().when(mMockSatelliteController)
                .isDtcSatelliteTechnologySupported(anyInt(), anyString());

        logd("case 1. plmn=unmatched, isNtn=true, satTech=unknown, connectType=manual, rat=lte");
        NetworkRegistrationInfo unmatchedPlmnNri =
                createNetworkRegistrationInfo(
                        VISITING_PLMN, true, NT_RADIO_TECHNOLOGY_UNKNOWN, NETWORK_TYPE_LTE);
        NetworkRegistrationInfo originalNri = new NetworkRegistrationInfo(unmatchedPlmnNri);
        assertEquals(unmatchedPlmnNri, originalNri);
        assertTrue(unmatchedPlmnNri.isNonTerrestrialNetwork());
        assertEquals(NT_RADIO_TECHNOLOGY_UNKNOWN, unmatchedPlmnNri.getSatelliteTechnology());

        doReturn(List.of(NT_RADIO_TECHNOLOGY_NR_NTN, NT_RADIO_TECHNOLOGY_LTE_DTC))
                .when(mMockSatelliteController)
                .getSupportedSatelliteTechnologies(anyInt(), anyString());
        doReturn(true).when(mMockSatelliteController).isSatelliteEnabledOrBeingEnabled();

        NtnCapabilityResolver.resolveNtnCapability(unmatchedPlmnNri, SUB_ID);
        assertTrue(unmatchedPlmnNri.isNonTerrestrialNetwork());
        assertNotEquals("unmatchedPlmnNri should not be the same with originalNri",
                unmatchedPlmnNri, originalNri);
        assertEquals(NT_RADIO_TECHNOLOGY_NB_IOT_NTN, unmatchedPlmnNri.getSatelliteTechnology());

        logd("case 2. plmn=unmatched, isNtn=true, satTech=unknown, connectType=auto, rat=lte");
        unmatchedPlmnNri = createNetworkRegistrationInfo(
                        VISITING_PLMN, true, NT_RADIO_TECHNOLOGY_UNKNOWN, NETWORK_TYPE_LTE);
        originalNri = new NetworkRegistrationInfo(unmatchedPlmnNri);
        assertEquals(unmatchedPlmnNri, originalNri);
        assertTrue(unmatchedPlmnNri.isNonTerrestrialNetwork());
        assertEquals(NT_RADIO_TECHNOLOGY_UNKNOWN, unmatchedPlmnNri.getSatelliteTechnology());
        doReturn(List.of(NT_RADIO_TECHNOLOGY_NR_NTN, NT_RADIO_TECHNOLOGY_LTE_DTC))
                .when(mMockSatelliteController)
                .getSupportedSatelliteTechnologies(anyInt(), anyString());
        doReturn(false).when(mMockSatelliteController).isSatelliteEnabledOrBeingEnabled();
        NtnCapabilityResolver.resolveNtnCapability(unmatchedPlmnNri, SUB_ID);
        assertTrue(unmatchedPlmnNri.isNonTerrestrialNetwork());
        assertNotEquals("unmatchedPlmnNri should not be the same with originalNri",
                unmatchedPlmnNri, originalNri);
        assertEquals(NT_RADIO_TECHNOLOGY_LTE_DTC, unmatchedPlmnNri.getSatelliteTechnology());

        logd("case 3. plmn=unmatched, isNtn=true, satTech=unknown, connectType=auto, rat=nr");
        unmatchedPlmnNri = createNetworkRegistrationInfo(
                VISITING_PLMN, true, NT_RADIO_TECHNOLOGY_UNKNOWN, NETWORK_TYPE_NR);
        originalNri = new NetworkRegistrationInfo(unmatchedPlmnNri);
        assertEquals(unmatchedPlmnNri, originalNri);
        assertTrue(unmatchedPlmnNri.isNonTerrestrialNetwork());
        assertEquals(NT_RADIO_TECHNOLOGY_UNKNOWN, unmatchedPlmnNri.getSatelliteTechnology());
        doReturn(List.of(NT_RADIO_TECHNOLOGY_NR_NTN, NT_RADIO_TECHNOLOGY_LTE_DTC))
                .when(mMockSatelliteController)
                .getSupportedSatelliteTechnologies(anyInt(), anyString());
        doReturn(false).when(mMockSatelliteController).isSatelliteEnabledOrBeingEnabled();
        NtnCapabilityResolver.resolveNtnCapability(unmatchedPlmnNri, SUB_ID);
        assertTrue(unmatchedPlmnNri.isNonTerrestrialNetwork());
        assertNotEquals("unmatchedPlmnNri should not be the same with originalNri",
                unmatchedPlmnNri, originalNri);
        assertEquals(NT_RADIO_TECHNOLOGY_NR_DTC, unmatchedPlmnNri.getSatelliteTechnology());

        logd("case 4. plmn=unmatched, isNtn=true, satTech=unknown, connectType=auto, rat=nr");
        unmatchedPlmnNri = createNetworkRegistrationInfo(
                VISITING_PLMN, true, NT_RADIO_TECHNOLOGY_UNKNOWN, NETWORK_TYPE_NR);
        originalNri = new NetworkRegistrationInfo(unmatchedPlmnNri);
        assertEquals(unmatchedPlmnNri, originalNri);
        assertTrue(unmatchedPlmnNri.isNonTerrestrialNetwork());
        assertEquals(NT_RADIO_TECHNOLOGY_UNKNOWN, unmatchedPlmnNri.getSatelliteTechnology());
        doReturn(List.of(NT_RADIO_TECHNOLOGY_LTE_DTC)).when(mMockSatelliteController)
                .getSupportedSatelliteTechnologies(anyInt(), anyString());
        doReturn(false).when(mMockSatelliteController).isSatelliteEnabledOrBeingEnabled();
        NtnCapabilityResolver.resolveNtnCapability(unmatchedPlmnNri, SUB_ID);
        assertTrue(unmatchedPlmnNri.isNonTerrestrialNetwork());
        assertNotEquals("unmatchedPlmnNri should not be the same with originalNri",
                unmatchedPlmnNri, originalNri);
        assertEquals(NT_RADIO_TECHNOLOGY_NR_DTC, unmatchedPlmnNri.getSatelliteTechnology());
    }

    @Test
    public void testResolveNtnCapability_whenIsNtnDisabled() {
        logd("testResolveNtnCapability_whenIsNtnDisabled");
        doCallRealMethod().when(mMockSatelliteController)
                .isDtcSatelliteTechnologySupported(anyInt(), anyString());

        logd("case 1. matchedPlmn=false, DTC=false >> isNtn=false, services=[DATA]");
        NetworkRegistrationInfo unmatchedPlmnNri =
                createNetworkRegistrationInfo(VISITING_PLMN, false, true);
        NetworkRegistrationInfo originalNri = new NetworkRegistrationInfo(unmatchedPlmnNri);
        assertEquals(unmatchedPlmnNri, originalNri);
        assertFalse(unmatchedPlmnNri.isNonTerrestrialNetwork());
        assertNotEquals(new HashSet<>(mSatelliteSupportedServiceList),
                new HashSet<>(unmatchedPlmnNri.getAvailableServices()));
        assertNotEquals(new HashSet<>(CARRIER_ROAMING_SATELLITE_DEFAULT_SERVICES),
                new HashSet<>(unmatchedPlmnNri.getAvailableServices()));
        assertEquals(new HashSet<>(List.of(SERVICE_TYPE_DATA)),
                new HashSet<>(unmatchedPlmnNri.getAvailableServices()));
        doReturn(List.of(NT_RADIO_TECHNOLOGY_NR_NTN)).when(mMockSatelliteController)
                .getSupportedSatelliteTechnologies(anyInt(), anyString());

        NtnCapabilityResolver.resolveNtnCapability(unmatchedPlmnNri, SUB_ID);
        assertFalse(unmatchedPlmnNri.isNonTerrestrialNetwork());
        assertEquals("unmatchedPlmnNri should be the same with originalNri",
                unmatchedPlmnNri, originalNri);

        logd("case 2. matchedPlmn=false, DTC=true >> isNtn=false, services=[DATA]");
        unmatchedPlmnNri =
                createNetworkRegistrationInfo(VISITING_PLMN, false, true);
        originalNri = new NetworkRegistrationInfo(unmatchedPlmnNri);
        assertEquals(unmatchedPlmnNri, originalNri);
        assertFalse(unmatchedPlmnNri.isNonTerrestrialNetwork());
        assertNotEquals(new HashSet<>(mSatelliteSupportedServiceList),
                new HashSet<>(unmatchedPlmnNri.getAvailableServices()));
        assertNotEquals(new HashSet<>(CARRIER_ROAMING_SATELLITE_DEFAULT_SERVICES),
                new HashSet<>(unmatchedPlmnNri.getAvailableServices()));
        assertEquals(new HashSet<>(List.of(SERVICE_TYPE_DATA)),
                new HashSet<>(unmatchedPlmnNri.getAvailableServices()));
        doReturn(List.of(NT_RADIO_TECHNOLOGY_NR_DTC)).when(mMockSatelliteController)
                .getSupportedSatelliteTechnologies(anyInt(), anyString());

        NtnCapabilityResolver.resolveNtnCapability(unmatchedPlmnNri, SUB_ID);
        assertFalse(unmatchedPlmnNri.isNonTerrestrialNetwork());
        assertEquals("unmatchedPlmnNri should be the same with originalNri",
                unmatchedPlmnNri, originalNri);

        logd("case 3. matchedPlmn=true, DTC=true >> isNtn=true, services=[SMS,EMERGENCY]");
        NetworkRegistrationInfo matchedPlmnNri =
                createNetworkRegistrationInfo(SATELLITE_PLMN, false, true);
        originalNri = new NetworkRegistrationInfo(matchedPlmnNri);
        assertEquals(matchedPlmnNri, originalNri);
        assertFalse(matchedPlmnNri.isNonTerrestrialNetwork());
        assertNotEquals(new HashSet<>(mSatelliteSupportedServiceList),
                new HashSet<>(matchedPlmnNri.getAvailableServices()));
        assertNotEquals(new HashSet<>(CARRIER_ROAMING_SATELLITE_DEFAULT_SERVICES),
                new HashSet<>(matchedPlmnNri.getAvailableServices()));
        assertEquals(new HashSet<>(List.of(SERVICE_TYPE_DATA)),
                new HashSet<>(matchedPlmnNri.getAvailableServices()));
        doReturn(List.of(NT_RADIO_TECHNOLOGY_NR_DTC)).when(mMockSatelliteController)
                .getSupportedSatelliteTechnologies(anyInt(), anyString());

        NtnCapabilityResolver.resolveNtnCapability(matchedPlmnNri, SUB_ID);
        assertNotEquals("matchedPlmnNri should not be the same with originalNri",
                matchedPlmnNri, originalNri);
        assertTrue(matchedPlmnNri.isNonTerrestrialNetwork());
        assertEquals(new HashSet<>(mSatelliteSupportedServiceList),
                new HashSet<>(matchedPlmnNri.getAvailableServices()));

        logd("case 4. matchedPlmn=true, DTC=false >> isNtn=false, services=[DATA]");
        matchedPlmnNri = createNetworkRegistrationInfo(SATELLITE_PLMN, false, true);
        originalNri = new NetworkRegistrationInfo(matchedPlmnNri);
        assertEquals(matchedPlmnNri, originalNri);
        assertFalse(matchedPlmnNri.isNonTerrestrialNetwork());
        assertNotEquals(new HashSet<>(mSatelliteSupportedServiceList),
                new HashSet<>(matchedPlmnNri.getAvailableServices()));
        assertNotEquals(new HashSet<>(CARRIER_ROAMING_SATELLITE_DEFAULT_SERVICES),
                new HashSet<>(matchedPlmnNri.getAvailableServices()));
        assertEquals(new HashSet<>(List.of(SERVICE_TYPE_DATA)),
                new HashSet<>(matchedPlmnNri.getAvailableServices()));
        doReturn(List.of(NT_RADIO_TECHNOLOGY_NR_NTN)).when(mMockSatelliteController)
                .getSupportedSatelliteTechnologies(anyInt(), anyString());

        NtnCapabilityResolver.resolveNtnCapability(matchedPlmnNri, SUB_ID);
        assertEquals("matchedPlmnNri should be the same with originalNri",
                matchedPlmnNri, originalNri);
    }

    private NetworkRegistrationInfo createNetworkRegistrationInfo(@NonNull String registeredPlmn) {
        return createNetworkRegistrationInfo(registeredPlmn, false, true);
    }

    private NetworkRegistrationInfo createNetworkRegistrationInfo(
            @NonNull String registeredPlmn, boolean isNtn, boolean isInService) {
        List<Integer> availableServices = new ArrayList<>();
        availableServices.add(SERVICE_TYPE_DATA);
        CellIdentity cellIdentity = new CellIdentityGsm(
                0, 0, 0, 0, "mcc", "mnc", "", "", new ArraySet<>());
        return new NetworkRegistrationInfo.Builder()
                .setDomain(DOMAIN_PS)
                .setTransportType(TRANSPORT_TYPE_WWAN)
                .setRegistrationState(
                        isInService ? REGISTRATION_STATE_ROAMING : REGISTRATION_STATE_UNKNOWN)
                .setAccessNetworkTechnology(isNtn ? NETWORK_TYPE_NR : NETWORK_TYPE_UNKNOWN)
                .setSatelliteTechnology(
                        isNtn ? NT_RADIO_TECHNOLOGY_NR_NTN : NT_RADIO_TECHNOLOGY_UNKNOWN)
                .setRejectCause(0)
                .setEmergencyOnly(false)
                .setAvailableServices(availableServices)
                .setCellIdentity(cellIdentity)
                .setRegisteredPlmn(registeredPlmn)
                .setIsNonTerrestrialNetwork(isNtn)
                .build();
    }

    private NetworkRegistrationInfo createNetworkRegistrationInfo(
            @NonNull String registeredPlmn, boolean isNtn, int satTech, int rat) {
        List<Integer> availableServices = new ArrayList<>();
        availableServices.add(SERVICE_TYPE_DATA);
        CellIdentity cellIdentity = new CellIdentityGsm(
                0, 0, 0, 0, "mcc", "mnc", "", "", new ArraySet<>());
        return new NetworkRegistrationInfo.Builder()
                .setDomain(DOMAIN_PS)
                .setTransportType(TRANSPORT_TYPE_WWAN)
                .setRegistrationState(REGISTRATION_STATE_ROAMING)
                .setAccessNetworkTechnology(rat)
                .setSatelliteTechnology(satTech)
                .setRejectCause(0)
                .setEmergencyOnly(false)
                .setAvailableServices(availableServices)
                .setCellIdentity(cellIdentity)
                .setRegisteredPlmn(registeredPlmn)
                .setIsNonTerrestrialNetwork(isNtn)
                .build();
    }
}
