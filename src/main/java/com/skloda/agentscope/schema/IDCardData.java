package com.skloda.agentscope.schema;

/**
 * Structured output schema for ID card information extraction.
 */
public class IDCardData {

    public String name;
    public String gender;
    public String ethnicity;
    public String birthDate;
    public String address;
    public String idNumber;
    public String issuingAuthority;
    public String validPeriod;
    public String side; // front or back

    public IDCardData() {}
}
