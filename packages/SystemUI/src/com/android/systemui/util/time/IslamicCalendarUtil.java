/*
 * Copyright (C) 2024-2025 MistOS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.util.time;

import android.icu.util.IslamicCalendar;

public class IslamicCalendarUtil {

    private static final String[] MONTH_NAMES = new String[] {
            "محرم", "صفر", "ربيع الأول", "ربيع الثاني", "جمادى الأولى", "جمادى الآخرة",
            "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة"
    };

    private static final String[] MONTH_NAMES_TRANSLITERATED = new String[] {
            "Muharram", "Safar", "Rabi' al-awwal", "Rabi' al-thani",
            "Jumada al-awwal", "Jumada al-thani",
            "Rajab", "Sha'ban", "Ramadan", "Shawwal", "Dhu al-Qidah", "Dhu al-Hijjah"
    };

    private static final String[] DIGITS_ARABIC_INDIC = new String[] {
            "٠", "١", "٢", "٣", "٤", "٥", "٦", "٧", "٨", "٩"
    };

    public static final int FLAG_INCLUDE_DATE = 1;
    public static final int FLAG_INCLUDE_MONTH = 1 << 1;
    public static final int FLAG_INCLUDE_YEAR = 1 << 2;
    public static final int FLAG_USE_TRANSLITERATION = 1 << 3;
    public static final int FLAG_USE_ARABIC_INDIC_DIGITS = 1 << 4;

    private IslamicCalendarUtil() {}

    public static String getIslamicDateString() {
        return getIslamicDateString(FLAG_INCLUDE_MONTH | FLAG_INCLUDE_DATE);
    }

    public static String getIslamicDateString(int flag) {
        return getIslamicDateString(new IslamicCalendar(), flag);
    }

    public static String getIslamicDateString(IslamicCalendar calendar, int flag) {
        StringBuilder sb = new StringBuilder();
        boolean useTransliteration = (flag & FLAG_USE_TRANSLITERATION) == FLAG_USE_TRANSLITERATION;
        boolean useArabicIndicDigits = (flag & FLAG_USE_ARABIC_INDIC_DIGITS) == FLAG_USE_ARABIC_INDIC_DIGITS;

        if ((flag & FLAG_INCLUDE_YEAR) == FLAG_INCLUDE_YEAR) {
            int year = calendar.get(IslamicCalendar.YEAR);
            sb.append(convYear(year, useArabicIndicDigits));
            sb.append(" ");
        }

        if ((flag & FLAG_INCLUDE_MONTH) == FLAG_INCLUDE_MONTH) {
            int month = calendar.get(IslamicCalendar.MONTH);
            sb.append(convMonth(month, useTransliteration));
        }

        if ((flag & FLAG_INCLUDE_DATE) == FLAG_INCLUDE_DATE) {
            int date = calendar.get(IslamicCalendar.DATE);
            if (sb.length() > 0 && sb.charAt(sb.length() - 1) != ' ') {
                sb.append(" ");
            }
            sb.append(convDate(date, useArabicIndicDigits));
        }

        return sb.toString().trim();
    }

    private static String convYear(int year, boolean useArabicIndicDigits) {
        String y = useArabicIndicDigits ? toArabicIndicDigits(year) : String.valueOf(year);
        return y + " هـ";
    }

    private static String convMonth(int month, boolean useTransliteration) {
        if (month >= 0 && month < MONTH_NAMES.length) {
            return useTransliteration ? MONTH_NAMES_TRANSLITERATED[month] : MONTH_NAMES[month];
        }
        return "";
    }

    private static String convDate(int date, boolean useArabicIndicDigits) {
        return useArabicIndicDigits ? toArabicIndicDigits(date) : String.valueOf(date);
    }

    private static String toArabicIndicDigits(int number) {
        String s = String.valueOf(number);
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            if (Character.isDigit(c)) {
                sb.append(DIGITS_ARABIC_INDIC[c - '0']);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
