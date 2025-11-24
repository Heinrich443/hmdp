package com.hmdp.utils;

import cn.hutool.core.util.StrUtil;

import static com.hmdp.utils.RegexPatterns.*;

public class RegexUtils {

    public static Boolean isPhoneInvalid(String phone) {
        return mismatch(phone, PHONE_REGEX);
    }

    public static Boolean isEmailInvalid(String email) {
        return mismatch(email, EMAIL_REGEX);
    }

    public static Boolean isPasswordInvalid(String password) {
        return mismatch(password, PASSWORD_REGEX);
    }

    public static Boolean isCodeInvalid(String code) {
        return mismatch(code, VERIFY_CODE_REGEX);
    }

    private static Boolean mismatch(String str, String regex) {
        if (StrUtil.isBlank(str)) {
            return true;
        }
        return !str.matches(regex);
    }
}
