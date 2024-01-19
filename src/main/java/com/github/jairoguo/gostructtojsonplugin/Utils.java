package com.github.jairoguo.gostructtojsonplugin;

import com.goide.psi.*;
import com.google.gson.GsonBuilder;
import com.intellij.psi.PsiElement;

import java.text.SimpleDateFormat;
import java.util.*;



public class Utils {

    private static final Map<String, Object> basicTypes = new HashMap<>();

    static {
        basicTypes.put("bool", false);
        basicTypes.put("byte", 0);
        basicTypes.put("int", 0);
        basicTypes.put("uint", 0);
        basicTypes.put("uint8", 255);
        basicTypes.put("uint16", 65535);
        basicTypes.put("uint32", 4294967295L);
        basicTypes.put("uint64", 1844674407370955161L);
        basicTypes.put("int8", -128);
        basicTypes.put("int16", -32768);
        basicTypes.put("int32", -2147483648);
        basicTypes.put("int64", -9223372036854775808L);
        basicTypes.put("uintptr", 0); // uintptr is an integer type that is large enough to hold the bit pattern of any pointer
        basicTypes.put("rune", 0);  // rune is an alias for int32 and is equivalent to int32 in all ways
        basicTypes.put("long", 0L);
        basicTypes.put("float32", 0.0F);
        basicTypes.put("float64", 0.0F);
        basicTypes.put("string", "");
        basicTypes.put("time.Time", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
    }

    public static boolean isBasicType(String typeName) {
        return basicTypes.containsKey(typeName);
    }

    public static String convertGoStructToJson(GoStructType goStructType) {

        Map<String, Object> map = buildMap(goStructType);
        return new GsonBuilder().serializeNulls().setPrettyPrinting().create().toJson(map);
    }

    private static Map<String, Object> buildMap(GoStructType goStructType) {
        Map<String, Object> map = new LinkedHashMap<>();

        List<GoFieldDeclaration> fieldsDeclareList = goStructType.getFieldDeclarationList();

        for (GoFieldDeclaration field : fieldsDeclareList) {
            GoType fieldType = field.getType();

            if (fieldType == null) {
                GoAnonymousFieldDefinition anonymous = field.getAnonymousFieldDefinition();
                if (anonymous != null) {
                    GoStructType structType = getStructType(anonymous);
                    if (structType != null) {
                        Map<String, Object> tmpMap = buildMap(structType);
                        map.putAll(tmpMap);
                    }
                }
            } else {

                String jsonKey = getFieldName(field);
                if (jsonKey.equals("-")) {
                    continue;
                }
                Object typeValue = getFieldTypeValue(field, fieldType);
                map.put(jsonKey, typeValue);

            }
        }
        return map;
    }

    private static String getFieldName(GoFieldDeclaration field) {
        String fieldName = field.getFieldDefinitionList().get(0).getIdentifier().getText();
        var ret = "-";

        char c = fieldName.charAt(0);
        if (!Character.isUpperCase(c)) {
            return ret;
        }


        var tag = field.getTag();
        if (tag != null) {
            var jsonTagValue = tag.getValue("json");
            if (jsonTagValue != null) {
                var realTag = jsonTagValue.split(",")[0];
                if (!realTag.isEmpty() && !realTag.equals("-")) {
                    ret = realTag;
                }
            }
        } else {
            ret = fieldName;
        }
        return ret;
    }

    private static String getFieldType(GoFieldDeclaration field) {
        var ret = "";

        var fieldTag = field.getTag();
        if (fieldTag != null) {
            var jsonTagValue = fieldTag.getValue("json");
            if (jsonTagValue != null) {
                var tags = jsonTagValue.split(",");
                if (tags.length >= 2) {
                    return Arrays.stream(tags).filter(tag -> !tag.isEmpty() && "string".equals(tag)).findFirst().orElse("");

                }

            }
        }
        return ret;
    }

    private static String getTypeText(GoType type) {
        return type == null ? "NOTBASICTYPE" : type.getText();
    }


    private static GoStructType getStructType(GoType goType) {
        GoTypeReferenceExpression typeRef = goType.getTypeReferenceExpression();
        return getStruct(typeRef);
    }

    private static GoStructType getStructType(GoAnonymousFieldDefinition anonymous) {
        GoTypeReferenceExpression typeRef = anonymous.getTypeReferenceExpression();
        return getStruct(typeRef);
    }

    private static GoStructType getStruct(GoTypeReferenceExpression typeRef) {
        PsiElement resolve = typeRef != null ? typeRef.resolve() : null;
        if (resolve instanceof GoTypeSpec typeSpec) {
            GoType type = typeSpec.getSpecType().getType();
            if (type instanceof GoStructType structType) {
                return structType;
            }
        }

        return null;
    }

    private static Object getFieldTypeValue(GoFieldDeclaration field, GoType type) {

        Object value = null;
        assert type != null;
        String typeText = getTypeText(type);

        if (isBasicType(typeText)) {
            String stringType = getFieldType(field);
            if (!stringType.isEmpty()) {
                value = basicTypes.get(stringType);
            } else {
                value = basicTypes.get(typeText);
            }
        } else if (type instanceof GoStructType structType) {
            value = buildMap(structType);
        } else if (type instanceof GoPointerType pointerType) {
            value = getFieldTypeValue(field, pointerType.getType());
        } else if (type instanceof GoArrayOrSliceType arrayOrSliceType) {
            value = Collections.singletonList(getFieldTypeValue(field, arrayOrSliceType.getType()));
        } else {
            GoStructType structType = getStructType(type);
            if (structType != null) {
                value = buildMap(structType);
            }
        }
        return value;

    }

    private Utils() {
    }
}