package me.nawa.wallet.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TripExpenseLinkMapper {

    void insert(
        @Param("tripId") Long tripId,
        @Param("ledgerEntryId") Long ledgerEntryId,
        @Param("appointmentMemberId") Long appointmentMemberId
    );
}

