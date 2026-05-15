package com.skloda.agentscope.schema;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IDCardDataTest {

    @Test
    void constructor_createsEmptyIDCardData() {
        IDCardData idCard = new IDCardData();

        assertNotNull(idCard);
        assertNull(idCard.name);
        assertNull(idCard.gender);
        assertNull(idCard.ethnicity);
        assertNull(idCard.birthDate);
        assertNull(idCard.address);
        assertNull(idCard.idNumber);
        assertNull(idCard.issuingAuthority);
        assertNull(idCard.validPeriod);
        assertNull(idCard.side);
    }

    @Test
    void fieldsCanBeSet() {
        IDCardData idCard = new IDCardData();

        idCard.name = "张三";
        idCard.gender = "男";
        idCard.ethnicity = "汉族";
        idCard.birthDate = "1990-01-01";
        idCard.address = "北京市朝阳区某某街道";
        idCard.idNumber = "110101199001011234";
        idCard.issuingAuthority = "北京市公安局朝阳分局";
        idCard.validPeriod = "2020.01.01-2030.01.01";
        idCard.side = "front";

        assertEquals("张三", idCard.name);
        assertEquals("男", idCard.gender);
        assertEquals("汉族", idCard.ethnicity);
        assertEquals("1990-01-01", idCard.birthDate);
        assertEquals("北京市朝阳区某某街道", idCard.address);
        assertEquals("110101199001011234", idCard.idNumber);
        assertEquals("北京市公安局朝阳分局", idCard.issuingAuthority);
        assertEquals("2020.01.01-2030.01.01", idCard.validPeriod);
        assertEquals("front", idCard.side);
    }

    @Test
    void backSide_fieldsCanBeSet() {
        IDCardData idCard = new IDCardData();

        idCard.side = "back";

        assertEquals("back", idCard.side);
    }
}
