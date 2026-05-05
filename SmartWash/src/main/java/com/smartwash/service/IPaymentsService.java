package com.smartwash.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.smartwash.entity.Payments;
import com.smartwash.from.payment.AddPaymentFrom;
import com.smartwash.from.payment.PaymentOrderFrom;
import com.smartwash.from.BaseSearchFrom;
import com.smartwash.from.payment.SearchPaymentFrom;
import com.smartwash.from.payment.UpdatePaymentFrom;
import com.smartwash.utils.LoginUser;
import com.smartwash.vo.payment.PaymentVo;

/**
 * <p>
 * 服务类
 * </p>
 *
 * @author
 * @since 2025-03-06
 */
public interface IPaymentsService extends IService<Payments> {

    Boolean addPayment(AddPaymentFrom paymentFrom);

    Boolean updatePayment(UpdatePaymentFrom paymentsFrom);

    Boolean deletePayments(String ids);

    Page<PaymentVo> getAllPayments(SearchPaymentFrom paymentFrom);

    Page<PaymentVo> getUserPayments(Long userId, BaseSearchFrom searchFrom);

    boolean paymentOrder(LoginUser user, PaymentOrderFrom orderFrom);
}
