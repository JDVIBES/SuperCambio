package com.supercambio.app;

import android.util.Base64;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class S7BQr {

    public static final String[][] BANKS = {
        {"0001", "Banco Central de Venezuela"},
        {"0102", "Banco de Venezuela"},
        {"0104", "Banco Venezolano de Crédito"},
        {"0105", "Banco Mercantil"},
        {"0108", "Banco Provincial"},
        {"0114", "Bancaribe"},
        {"0115", "Banco Exterior"},
        {"0128", "Banco Caroní"},
        {"0134", "Banesco"},
        {"0137", "Sofitasa"},
        {"0138", "Banco Plaza"},
        {"0146", "Banco de la Gente Emprendedora"},
        {"0151", "BFC Banco Fondo Común"},
        {"0156", "100% Banco"},
        {"0157", "DelSur"},
        {"0163", "Banco del Tesoro"},
        {"0166", "Banco Agrícola de Venezuela"},
        {"0168", "Bancrecer"},
        {"0169", "Mi Banco"},
        {"0171", "Banco Activo"},
        {"0172", "Bancamiga"},
        {"0173", "Banco Internacional de Desarrollo"},
        {"0174", "Banplus"},
        {"0175", "Banco Bicentenario"},
        {"0177", "Banfanb"},
        {"0178", "N58 Banco Digital"},
        {"0191", "Banco Nacional de Crédito"},
        {"0601", "Instituto Municipal de Crédito Popular"}
    };

    private static final String[][] ALIASES = {
        {"0001", "bcv", "banco central", "banco central de venezuela"},
        {"0102", "bdv", "banco de venezuela", "venezuela"},
        {"0104", "bvc", "venezolano de credito", "banco venezolano de credito"},
        {"0105", "mercantil", "banco mercantil"},
        {"0108", "provincial", "banco provincial", "bbva provincial"},
        {"0114", "caribe", "carribe", "bancaribe"},
        {"0115", "exterior", "banco exterior"},
        {"0128", "caroni", "banco caroni"},
        {"0134", "banesco"},
        {"0137", "sofitasa"},
        {"0138", "plaza", "banco plaza"},
        {"0146", "bangente", "gente emprendedora", "banco de la gente emprendedora"},
        {"0151", "bfc", "fondo comun", "banco fondo comun"},
        {"0156", "100", "100%", "100 banco", "cien por ciento"},
        {"0157", "delsur", "del sur", "banco delsur"},
        {"0163", "tesoro", "banco del tesoro"},
        {"0166", "agricola", "banco agricola", "banco agricola de venezuela"},
        {"0168", "bancrecer"},
        {"0169", "mi banco", "mibanco"},
        {"0171", "activo", "banco activo"},
        {"0172", "bancamiga"},
        {"0173", "bid", "desarrollo", "banco internacional de desarrollo"},
        {"0174", "banplus"},
        {"0175", "bicentenario", "bdt", "banco bicentenario"},
        {"0177", "banfanb", "fanb"},
        {"0178", "n58", "n 58", "n58 banco digital"},
        {"0191", "bnc", "nacional de credito", "banco nacional de credito"},
        {"0601", "imcp", "municipal", "instituto municipal de credito popular"}
    };

    private static final HashMap<String, String[]> AES = new HashMap<String, String[]>();

    static {
        AES.put("0001", new String[]{"07a6e51e8ba75f53dc589c2130c50531", "70b5e07da1864ca0"});
        AES.put("0102", new String[]{"871b17da0dd6d72e4bbfee126928e74c", "8313014167d9aa6d"});
        AES.put("0104", new String[]{"cb8e59064c0bf88b14316a0ab335ddbe", "b7e51099f9115933"});
        AES.put("0105", new String[]{"3ecb1467e397d0cd89f247069ea39ce2", "7e21f1685a7d0ccc"});
        AES.put("0108", new String[]{"18de784f22a3366328105ad4c472a528", "357eebe1bac885c8"});
        AES.put("0114", new String[]{"4eae63f8e3180fc3a9446c8c70c65486", "a1265b033bf5d11e"});
        AES.put("0115", new String[]{"a1f4c0dbff9e6b4d824253fa69d866b3", "eefee85d60d35310"});
        AES.put("0128", new String[]{"8640e5eb99d942704abe7143f3f4c730", "9333c6cb2891af16"});
        AES.put("0134", new String[]{"75da349d89b23601a09631981dcaefbb", "d4188e40bac8346a"});
        AES.put("0137", new String[]{"bcc25d8fad363de196e6b925de115c83", "746d47675f9a6322"});
        AES.put("0138", new String[]{"07630693c86dbd5d19fccbfba2ec96ed", "fc531fc9fc9730dc"});
        AES.put("0146", new String[]{"738eab494f7f11bd986e66ed7c636a3d", "991d0e54d165b8c6"});
        AES.put("0151", new String[]{"cd3f8b47fec691d4f94ce03ccbd7a6d8", "4c7b2f758879bc3d"});
        AES.put("0156", new String[]{"5419e44c0a1a14e9446c5552257c687e", "846f83240682e11c"});
        AES.put("0157", new String[]{"307824e31c58e5581cfc81ed7ade5320", "f4c90caf573b7246"});
        AES.put("0163", new String[]{"25c73294f912834e3bc008a55373c19e", "06c8b86ef5577276"});
        AES.put("0166", new String[]{"1272ed7f6779147431b68a4a8c3f8fab", "2e0fda6be443a4f4"});
        AES.put("0168", new String[]{"3e68efdc13d064a5f600fba626deb0ee", "0d005bc92718b109"});
        AES.put("0169", new String[]{"c035e4394623fd10a3c7862959bf0462", "96b310a7f5a77c17"});
        AES.put("0171", new String[]{"1a45fb2ca5f172f1c545f7db3f74cd55", "50c385ffa9edff1d"});
        AES.put("0172", new String[]{"0aa88797d77880f51a13cf2b13d374e9", "f73b9ea3804a7e97"});
        AES.put("0173", new String[]{"5cc5e99556ae25848a533e169856a9da", "3572d6c4a680505f"});
        AES.put("0174", new String[]{"ebb5b58cbe9245e0257c7df6c729b69c", "b5693ddea769629a"});
        AES.put("0175", new String[]{"f81049ec557c29b3e2f2cf1879b6f65a", "cbe81d67c314142b"});
        AES.put("0177", new String[]{"cf57c12bcd4994d603562a0140c271e0", "f1f52869e578d059"});
        AES.put("0178", new String[]{"c64ae9e861454d41e41966beff372f7a", "1a8ae60d14eac9da"});
        AES.put("0191", new String[]{"a1f9a96ebf68f1d8c98ec09d9a8d21d0", "fa4bfdc9bdab498c"});
        AES.put("0601", new String[]{"f7ee5e88f2cc08bf6ba966840a2ebdf0", "809dd14eab7cb658"});
    }

    private S7BQr() {
    }

    public static String encode(String bankValue, String phoneValue, String idValue, String nameValue) throws Exception {
        String bank = codeFor(bankValue);
        if (bank == null) {
            throw new IllegalArgumentException("Banco no válido");
        }
        String phone = normalizePhone(phoneValue);
        if (phone == null) {
            throw new IllegalArgumentException("Teléfono no válido");
        }
        String id = normalizeId(idValue);
        if (id == null) {
            throw new IllegalArgumentException("Cédula no válida");
        }
        JSONObject json = new JSONObject();
        json.put("bank", bank);
        json.put("phone", phone);
        json.put("id", id);
        String name = nameValue == null ? "" : nameValue.trim();
        if (name.length() > 0) {
            json.put("name", name);
        }
        String encrypted = encrypt(json.toString(), bank);
        return encrypted + "?merchantId=" + bank + "&origin=app";
    }

    public static String codeFor(String value) {
        if (value == null) {
            return null;
        }
        String raw = value.trim();
        if (raw.length() == 0) {
            return null;
        }
        if (raw.length() == 4 && isDigits(raw) && AES.containsKey(raw)) {
            return raw;
        }
        String norm = normalizeName(raw);
        if (norm.length() == 0) {
            return null;
        }
        for (int i = 0; i < BANKS.length; i++) {
            if (normalizeName(BANKS[i][1]).equals(norm)) {
                return BANKS[i][0];
            }
        }
        for (int i = 0; i < ALIASES.length; i++) {
            for (int j = 1; j < ALIASES[i].length; j++) {
                if (normalizeName(ALIASES[i][j]).equals(norm)) {
                    return ALIASES[i][0];
                }
            }
        }
        for (int i = 0; i < BANKS.length; i++) {
            String bankName = normalizeName(BANKS[i][1]);
            if (bankName.contains(norm) || norm.contains(bankName)) {
                return BANKS[i][0];
            }
        }
        return null;
    }

    public static String bankName(String value) {
        String code = codeFor(value);
        if (code == null) {
            return value == null ? "" : value.trim();
        }
        for (int i = 0; i < BANKS.length; i++) {
            if (BANKS[i][0].equals(code)) {
                return BANKS[i][1];
            }
        }
        return code;
    }

    public static String normalizePhone(String value) {
        if (value == null) {
            return null;
        }
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= '0' && c <= '9') {
                digits.append(c);
            }
        }
        String phone = digits.toString();
        if (phone.length() == 11 && phone.charAt(0) == '0') {
            phone = "58" + phone.substring(1);
        } else if (phone.length() == 10 && phone.charAt(0) == '4') {
            phone = "58" + phone;
        }
        if (phone.length() != 12 || !phone.startsWith("58")) {
            return null;
        }
        return phone;
    }

    public static String normalizeId(String value) {
        if (value == null) {
            return null;
        }
        String raw = value.trim().toUpperCase(LocaleHolder.LOCALE);
        StringBuilder clean = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if ((c >= '0' && c <= '9') || (c >= 'A' && c <= 'Z')) {
                clean.append(c);
            }
        }
        String id = clean.toString();
        if (id.length() == 0) {
            return null;
        }
        char first = id.charAt(0);
        if (first >= '0' && first <= '9') {
            return "V" + id;
        }
        if ("VJGE".indexOf(first) < 0) {
            return null;
        }
        if (id.length() < 2) {
            return null;
        }
        for (int i = 1; i < id.length(); i++) {
            char c = id.charAt(i);
            if (c < '0' || c > '9') {
                return null;
            }
        }
        return id;
    }

    private static String encrypt(String plain, String bank) throws Exception {
        String[] key = AES.get(bank);
        if (key == null) {
            throw new IllegalArgumentException("Sin clave para " + bank);
        }
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        SecretKeySpec keySpec = new SecretKeySpec(key[0].getBytes(StandardCharsets.US_ASCII), "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(key[1].getBytes(StandardCharsets.US_ASCII));
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
        byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
        return Base64.encodeToString(encrypted, Base64.NO_WRAP);
    }

    private static boolean isDigits(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    private static String normalizeName(String value) {
        String lower = value.toLowerCase(LocaleHolder.LOCALE);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (c == 'á' || c == 'à' || c == 'ä' || c == 'â') c = 'a';
            else if (c == 'é' || c == 'è' || c == 'ë' || c == 'ê') c = 'e';
            else if (c == 'í' || c == 'ì' || c == 'ï' || c == 'î') c = 'i';
            else if (c == 'ó' || c == 'ò' || c == 'ö' || c == 'ô') c = 'o';
            else if (c == 'ú' || c == 'ù' || c == 'ü' || c == 'û') c = 'u';
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static final class LocaleHolder {
        static final java.util.Locale LOCALE = new java.util.Locale("es", "VE");
    }
}
