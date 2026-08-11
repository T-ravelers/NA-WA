package me.nawa.wallet.mapper;

import me.nawa.wallet.domain.QrPaymentCode;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface  QrPaymentCodeMapper {

    void insert(QrPaymentCode qrPaymentCode);

}
