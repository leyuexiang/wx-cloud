package cn.iocoder.wx.module.iot.service;

import cn.iocoder.wx.module.iot.controller.admin.connector.vo.IotConnectorPageReqVO;
import cn.iocoder.wx.module.iot.controller.admin.connector.vo.IotMqttConfigValidateReqVO;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 校验沿用平台 1—100 条分页上限，预校验请求也限制输入体积。 */
class IotConnectorManagementValidationTest {
    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();

    @AfterAll
    static void close() { FACTORY.close(); }

    @Test
    void shouldRejectUnboundedAndInvalidPaging() {
        var page = new IotConnectorPageReqVO();
        assertTrue(FACTORY.getValidator().validate(page).isEmpty());
        for (Integer size : new Integer[]{-1, 0, 101, null}) {
            page.setPageSize(size);
            assertFalse(FACTORY.getValidator().validate(page).isEmpty());
        }
        page.setPageSize(100);
        page.setPageNo(0);
        assertFalse(FACTORY.getValidator().validate(page).isEmpty());
    }

    @Test
    void shouldRejectUnknownProtocolFilterAndOversizedIdentifiers() {
        var page = new IotConnectorPageReqVO();
        page.setConnectorType("mqtt");
        assertFalse(FACTORY.getValidator().validate(page).isEmpty());
        page.setConnectorType("MQTT");
        page.setConnectorId("x".repeat(65));
        assertFalse(FACTORY.getValidator().validate(page).isEmpty());
    }

    @Test
    void shouldBoundDraftAndRequireOnlyProtocolFields() {
        var request = new IotMqttConfigValidateReqVO();
        assertEquals(3, FACTORY.getValidator().validate(request).size());
        request.setEndpoint("ssl://broker.example.com:8883");
        request.setCredentialRef("IOT_MQTT_PILOT");
        request.setConfigJson("{\"topics\":[\"pilot/a\"],\"qos\":1}");
        assertTrue(FACTORY.getValidator().validate(request).isEmpty());
        request.setConfigJson("x".repeat(65537));
        assertFalse(FACTORY.getValidator().validate(request).isEmpty());
    }
}
