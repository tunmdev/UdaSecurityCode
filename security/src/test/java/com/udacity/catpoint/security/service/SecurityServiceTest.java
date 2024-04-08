package com.udacity.catpoint.security.service;

import com.udacity.catpoint.image.service.ImageService;
import com.udacity.catpoint.security.application.StatusListener;
import com.udacity.catpoint.security.data.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SecurityServiceTest {
    SecurityService securityService;

    private Sensor sensor;

    @Mock
    SecurityRepository securityRepository;

    @Mock
    ImageService imageService;

    @BeforeEach
    void setUp() {
        securityService = new SecurityService(securityRepository, imageService);
        sensor = new Sensor(UUID.randomUUID().toString(), SensorType.DOOR);
    }

    private Set<Sensor> getSensors(int numberOfSensors, boolean status) {
        return IntStream.range(0, numberOfSensors)
                .mapToObj(i -> new Sensor(UUID.randomUUID().toString(), SensorType.WINDOW))
                .peek(sensor -> sensor.setActive(status))
                .collect(Collectors.toSet());
    }

    // === TEST 1: If alarm is armed and a sensor becomes activated, put the system into pending alarm status. ===
    @ParameterizedTest
    @EnumSource(value = ArmingStatus.class, names = {"ARMED_HOME", "ARMED_AWAY"})
    void changeAlarmStatus_alarmArmedAndSensorActivated_alarmStatusPending(ArmingStatus armingStatus) {
        // Alarm is ARMED
        when(securityService.getArmingStatus()).thenReturn(armingStatus);
        when(securityService.getAlarmStatus()).thenReturn(AlarmStatus.NO_ALARM);

        // Sensor becomes activated
        sensor.setActive(false);
        securityService.changeSensorActivationStatus(sensor, true);

        verify(securityRepository, times(1)).setAlarmStatus(AlarmStatus.PENDING_ALARM);
    }

    // === TEST 2: If alarm is armed and a sensor becomes activated and the system is already pending alarm,
    // set the alarm status to alarm ===
    @ParameterizedTest
    @EnumSource(value = ArmingStatus.class, names = {"ARMED_HOME", "ARMED_AWAY"})
    void changeAlarmStatus_alarmAlreadyPendingAndSensorActive_alarmStatusAlarm(ArmingStatus armingStatus) {
        // Alarm already e.g: ARMED_HOME
        when(securityService.getArmingStatus()).thenReturn(armingStatus);

        // Already PENDING_ALARM
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);

        // Sensor become activated
        Sensor newSensor = new Sensor(UUID.randomUUID().toString(), SensorType.DOOR);
        newSensor.setActive(false);
        securityService.changeSensorActivationStatus(newSensor, true);

        verify(securityRepository, times(1)).setAlarmStatus(AlarmStatus.ALARM);
    }

    // === TEST 3: If pending alarm and all sensors are inactive, return to no alarm state. ===
    @Test
    void changeAlarmStatus_alarmPendingAndAllSensorInactive_changeToNoAlarm() {
        // Alarm pending
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);

        // Having 4 sensor inactive, suppose last sensor = active then change to inactive
        Set<Sensor> sensors = getSensors(4, false);
        Sensor lastSensor = sensors.iterator().next();
        lastSensor.setActive(true);
        securityService.changeSensorActivationStatus(lastSensor, false);

        verify(securityRepository, times(1)).setAlarmStatus(AlarmStatus.NO_ALARM);
    }

    // === TEST 4: If alarm is active, change in sensor state should not affect the alarm state. ===
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void changeAlarmState_alarmActiveAndSensorStateChanges_stateNotAffected(boolean state) {
        // Alarm active
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.ALARM);

        // Sensor state change
        sensor.setActive(state);
        securityService.changeSensorActivationStatus(sensor, !state);

        verify(securityRepository, never()).setAlarmStatus(any(AlarmStatus.class));
        verify(securityRepository, times(1)).updateSensor(sensor);
    }

    // === TEST 5: If a sensor is activated while already active and the system is in pending state,
    // change it to alarm state. ===
    @Test
    void changeAlarmState_sensorActivatedWhileAlreadyActiveAndAlarmPending_changeToAlarmState() {
        // Alarm in PENDING_ALARM state
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);

        Sensor newSensor = new Sensor(UUID.randomUUID().toString(), SensorType.DOOR);
        newSensor.setActive(true);
        // change state to same state = active
        securityService.changeSensorActivationStatus(newSensor, true);

        verify(securityRepository, times(1)).setAlarmStatus(AlarmStatus.ALARM);
    }

    // === TEST 6: If a sensor is deactivated while already inactive, make no changes to the alarm state. ===
    @ParameterizedTest
    @EnumSource(AlarmStatus.class)
    void changeAlarmState_sensorDeactivateWhileInactive_noChangeToAlarmState(AlarmStatus alarmStatus) {
        when(securityRepository.getAlarmStatus()).thenReturn(alarmStatus);

        // Deactivate sensor while already inactive
        sensor.setActive(false);
        securityService.changeSensorActivationStatus(sensor, false);

        verify(securityRepository, never()).setAlarmStatus(any(AlarmStatus.class));
    }

    // === TEST 7: If the image service identifies an image containing a cat while the system is armed-home,
    // put the system into alarm status. ===
    @Test
    void changeAlarmState_imageContainingCatDetectedAndSystemArmed_changeToAlarmStatus() {
        // System is armed-home
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);

        // image identifies image => a cat
        when(imageService.imageContainsCat(any(), anyFloat())).thenReturn(true);
        securityService.processImage(mock(BufferedImage.class));

        verify(securityRepository, times(1)).setAlarmStatus(AlarmStatus.ALARM);
    }

    // === TEST 8: If the image service identifies an image that does not contain a cat,
    // change the status to no alarm as long as the sensors are not active. ===
    @Test
    void changeAlarmState_noCatImageIdentifiedAndSensorsAreInactive_changeToAlarmStatus() {
        // All sensors are not active
        Set<Sensor> sensors = getSensors(2, false);
        Sensor newSensor = new Sensor(UUID.randomUUID().toString(), SensorType.DOOR);
        newSensor.setActive(false);
        securityService.addSensor(newSensor);
        securityService.removeSensor(newSensor);
        when(securityRepository.getSensors()).thenReturn(sensors);

        // image identifies image => not a cat
        when(imageService.imageContainsCat(any(), anyFloat())).thenReturn(false);
        securityService.processImage(mock(BufferedImage.class));

        verify(securityRepository, times(1)).setAlarmStatus(AlarmStatus.NO_ALARM);
    }

    // === TEST 9:If the system is disarmed, set the status to no alarm. ===
    @Test
    void changeAlarmStatus_systemDisArmed_changeToAlarmStatus() {
        securityService.setArmingStatus(ArmingStatus.DISARMED);

        verify(securityRepository, times(1)).setAlarmStatus(AlarmStatus.NO_ALARM);
    }

    // === TEST 10: If the system is armed, reset all sensors to inactive. ===
    @ParameterizedTest
    @EnumSource(value = ArmingStatus.class, names = {"ARMED_AWAY", "ARMED_HOME"})
    void updateSensors_systemArmed_deactivateAllSensors(ArmingStatus armingStatus) {
        // Init all sensor = active
        Set<Sensor> sensors = getSensors(4, true);
        when(securityRepository.getSensors()).thenReturn(sensors);
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);

        // System is armed
        securityService.setArmingStatus(armingStatus);

        assertAll(() -> securityService.getSensors().stream().forEach(sensor -> assertFalse(sensor.getActive())));
    }

    // === TEST 11: If the system is armed-home while the camera shows a cat, set the alarm status to alarm. ===
    @Test
    void changeAlarmStatus_systemArmedHomeAndCatDetected_changeToAlarmStatus() {
        // image identifies image => a cat
        when(imageService.imageContainsCat(any(), anyFloat())).thenReturn(true);
        securityService.processImage(mock(BufferedImage.class));

        // System is armed home
        securityService.setArmingStatus(ArmingStatus.ARMED_HOME);

        verify(securityRepository, times(1)).setAlarmStatus(AlarmStatus.ALARM);
    }

    // New Test 1: If a sensor change to deactivate and the system is already alarm,
    // set the alarm status to pending alarm ===
    @Test
    void changeAlarmStatus_alarmAlreadyAlarmAndSystemDisarmed_alarmStatusPending() {
        // Already ALARM
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.ALARM);

        // System disarmed
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.DISARMED);
        securityService.changeSensorActivationStatus(sensor, false);

        verify(securityRepository, times(1)).setAlarmStatus(AlarmStatus.PENDING_ALARM);
    }
}