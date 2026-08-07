package me.nawa.member.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MemberErrorCodeTest {

    @Test
    void errorCodes_useMemberPrefixAndExpectedStatuses() {
        assertEquals("MEMBER-001", MemberErrorCode.MEMBER_NOT_FOUND.getCode());
        assertEquals(HttpStatus.NOT_FOUND, MemberErrorCode.MEMBER_NOT_FOUND.getStatus());

        assertEquals("MEMBER-002", MemberErrorCode.UNSUPPORTED_LANGUAGE.getCode());
        assertEquals(HttpStatus.BAD_REQUEST, MemberErrorCode.UNSUPPORTED_LANGUAGE.getStatus());

        assertEquals("MEMBER-003", MemberErrorCode.UNSUPPORTED_CURRENCY.getCode());
        assertEquals(HttpStatus.BAD_REQUEST, MemberErrorCode.UNSUPPORTED_CURRENCY.getStatus());

        assertEquals("MEMBER-004", MemberErrorCode.NO_UPDATABLE_FIELD.getCode());
        assertEquals(HttpStatus.BAD_REQUEST, MemberErrorCode.NO_UPDATABLE_FIELD.getStatus());
    }
}
