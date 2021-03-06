package org.zywx.wbpalmstar.plugin.uexalipay;
import org.zywx.wbpalmstar.engine.EBrowserView;
import org.zywx.wbpalmstar.engine.universalex.EUExBase;
import org.zywx.wbpalmstar.engine.universalex.EUExCallback;

import android.content.Context;
import android.os.Handler;
import android.os.Message;

public class EUExAliPay extends EUExBase {

	static final String onFunction = "uexAliPay.onStatus";
	static final String SCRIPT_HEADER = "javascript:";

	private PFPayCallBack m_eCallBack;
	private boolean m_paying;

	public EUExAliPay(Context context, EBrowserView inParent) {
		super(context, inParent);
	}

	/**
	 * 初始化商家信息
	 * 
	 * @param params
	 */
	public void setPayInfo(String[] params) {
		if (params.length < 5) {
			return;
		}
		PFAlixpay alipay = PFAlixpay.get(mContext);
		String inPartner = params[0];
		String inSeller = params[1];
		String inRsaPrivate = params[2];
		String inRsaPublic = params[3];
		String inNotifyUrl = params[4];
		PayConfig congfig = new PayConfig(inPartner, inSeller, inRsaPrivate,
				inRsaPublic, inNotifyUrl, null);
		alipay.setPayConfig(congfig);
	}

	/**
	 * 支付
	 * 
	 * @param params
	 */
	public void pay(String[] params) {
		if (params.length < 4) {
			return;
		}
		String inTradeNum = params[0];
		String inSubject = params[1];
		String inBody = params[2];
		String inTotalFee = params[3];
		// 支付状态：0-成功,1-支付中,2-失败,3-支付插件不完整
		if (m_paying)
			return;
		m_paying = true;
		if (null == m_eCallBack) {
			m_eCallBack = new PFPayCallBack();
		}
		try {
			PFAlixpay alipay = PFAlixpay.get(mContext);
			PayConfig config = alipay.getPayConfig();
			if (null == config) {
				onCallback(SCRIPT_HEADER + "if(" + onFunction + "){"
						+ onFunction + "(" + EUExCallback.F_C_PAYFAILED + ",'"
						+ "config error!" + "');}");
				m_paying = false;
				return;
			}
			if (!alipay.checkApp()) {
				m_paying = false;
				onCallback(SCRIPT_HEADER + "if(" + onFunction + "){"
						+ onFunction + "(" + EUExCallback.F_C_PAYFAILED + ",'"
						+ "支付插件不完整" + "');}");
				return;
			}
			alipay.pay(inTradeNum, inSubject, inBody, inTotalFee, m_eCallBack, config);
		} catch (Exception e) {
			m_paying = false;
			errorCallback(0, 0, e.toString());
		}

	}

	private class PFPayCallBack extends Handler {
		@Override
		public void handleMessage(Message msg) {
			try {
				String strRet = (String) msg.obj;
				String js = "";
				switch (msg.what) {
				case AlixId.RQF_PAY: {
					try {
						ResultChecker resultChecker = new ResultChecker(strRet);
						int retVal = resultChecker.checkSign(PFAlixpay.get(mContext).getPayConfig());
						if (retVal == ResultChecker.RESULT_CHECK_SIGN_FAILED) { // 订单信息被非法篡改
							js = SCRIPT_HEADER + "if(" + onFunction + "){"
									+ onFunction + "("
									+ EUExCallback.F_C_PAYFAILED + ",'"
									+ "订单信息被非法篡改" + "');}";
							onCallback(js);
							return;
						} else {
							String code = (String) resultChecker.getJSONResult().get("resultStatus");
							int resultCode = Integer.valueOf(code.substring(1,code.length() - 1));
							String memo = resultChecker.getJSONResult().getString("memo").replace("{", "").replace("}", "");
							switch (resultCode) {
							case 9000:// 支付成功
								if (resultChecker.isPayOk(PFAlixpay.get(mContext).getPayConfig())) {
									js = SCRIPT_HEADER + "if(" + onFunction
											+ "){" + onFunction + "("
											+ EUExCallback.F_C_PAYSUCCSS + ",'"
											+ "支付成功" + "');}";
								} else {
									js = SCRIPT_HEADER + "if(" + onFunction
											+ "){" + onFunction + "("
											+ EUExCallback.F_C_PAYSUCCSS + ",'"
											+ "订单可能未支付成功,请联系支付宝公司进行确认" + "');}";
								}
								onCallback(js);
								break;
							case 4000:// 系统异常
							case 4001:// 数据格式不正确
							case 4003:// 该用户绑定的支付宝账户被冻结或不允许支付
							case 4004:// 该用户已解除绑定
							case 4005:// 绑定失败或没有绑定
							case 4006:// 订单支付失败
							case 4010:// 重新绑定账户
							case 6000:// 支付服务正在进行升级操作
							case 6001:// 用户中途取消支付操作
							case 6002:// 网络错误
								js = SCRIPT_HEADER + "if(" + onFunction + "){"
										+ onFunction + "("
										+ EUExCallback.F_C_PAYFAILED + ",'"
										+ memo + "');}";
								onCallback(js);
								break;
							default:
								js = SCRIPT_HEADER + "if(" + onFunction + "){"
										+ onFunction + "("
										+ EUExCallback.F_C_PAYFAILED + ",'"
										+ memo + "');}";
								onCallback(js);
								break;
							}
						}
					} catch (Exception e) { // 异常 提示信息为 strRet
						e.printStackTrace();
						errorCallback(0, 0, e.toString() + "//" + strRet);
					}
				}
					m_paying = false;
					break;
				}
				super.handleMessage(msg);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	protected boolean clean() {

		m_paying = false;
		if (null != m_eCallBack) {
			m_eCallBack = null;
		}
		return true;
	}

}