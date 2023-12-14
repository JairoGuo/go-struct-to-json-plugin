package com.github.jairoguo.gostructtojsonplugin;

import com.goide.psi.*;
import com.google.gson.GsonBuilder;
import com.intellij.psi.PsiElement;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class Utils {

    private static final Map<String, Object> basicTypes = new HashMap<>();
    private static final String STRUCT_TYPE = "STRUCT_TYPE";

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

    private static String getJsonKeyName(String fieldName, String tagText) {
        String jsonKey = fieldName;
        if (tagText == null || tagText.isEmpty()) {
            return jsonKey;
        }
        String regPattern = "[json]:\"([\\w\\d_,-\\.]+)\"";
        Pattern pattern = Pattern.compile(regPattern);
        Matcher matcher = pattern.matcher(tagText);
        if (matcher.find()) {
            String tmpKeyName = matcher.group(1).split(",")[0];
            if (!Objects.equals(tmpKeyName, "-")) { // for now,don't omit any field
                jsonKey = tmpKeyName;
            }
        }
        return jsonKey;
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
                String fieldName = field.getFieldDefinitionList().get(0).getIdentifier().getText();
                String fieldTagText = field.getTagText();
                GoTypeReferenceExpression typeRef = fieldType.getTypeReferenceExpression();
                String fieldTypeStr = typeRef == null ? "NOTBASICTYPE" : typeRef.getText();

                String jsonKey = getJsonKeyName(fieldName, fieldTagText);

                if (isBasicType(fieldTypeStr)) {
                    map.put(jsonKey, basicTypes.get(fieldTypeStr));
                }
                else if (fieldType instanceof GoStructType structType) {
                    Map<String, Object> tmpMap = buildMap(structType);
                    map.put(jsonKey, tmpMap);
                }
                else if (fieldType instanceof GoMapType mapType) {
                    Map<String, Object> tmpMap = new HashMap<>();
                    String tmpValueType = getTypeText(Objects.requireNonNull(mapType.getValueType()));
                    if (isBasicType(tmpValueType)) {
                        tmpMap.put("key", basicTypes.get(tmpValueType));
                    } else {
                        GoStructType structType = getStructType(mapType.getValueType());
                        if (structType != null) {
                            Map<String, Object> valueMap = buildMap(structType);
                            tmpMap.put("key", valueMap);
                        }
                    }
                    map.put(jsonKey, tmpMap);
                } else if (fieldType instanceof GoArrayOrSliceType arrayOrSliceType) {
                    ArrayList<Object> tmpList = new ArrayList<>();
                    String tmpStr = getTypeText(arrayOrSliceType.getType());
                    if (isBasicType(tmpStr)) {
                        tmpList.add(basicTypes.get(tmpStr));

                    } else if (arrayOrSliceType.getType() instanceof GoStructType structType) {
                        Map<String, Object> tmpMap = buildMap(structType);
                        tmpList.add(tmpMap);
                    } else {
                        GoStructType structType = getStructType(arrayOrSliceType.getType());
                        if (structType != null) {
                            Map<String, Object> tmpMap = buildMap(structType);
                            tmpList.add(tmpMap);
                        }
                    }
                    map.put(jsonKey, tmpList);
                } else if (fieldType instanceof GoPointerType pointerType) {
                    String typeText = getTypeText(pointerType.getType());
                    if (isBasicType(typeText)) {
                        map.put(jsonKey, basicTypes.get(typeText));
                    } else if (pointerType.getType() instanceof GoStructType structType) {
                        Map<String, Object> tmpMap = buildMap(structType);
                        map.put(jsonKey, tmpMap);
                    } else {
                        GoStructType structTypeOfPoint = getStructType(pointerType.getType());
                        Map<String, Object> tmpMap = buildMap(structTypeOfPoint);
                        map.put(jsonKey, tmpMap);
                    }

                } else if (fieldType instanceof GoInterfaceType) {
                    map.put(jsonKey, new HashMap<>());
                } else {
                    GoStructType structType = getStructType(fieldType);
                    if (structType != null) {
                        Map<String, Object> tmpMap = buildMap(structType);
                        map.put(jsonKey, tmpMap);
                    }

                }
            }
        }
        return map;
    }


    static String getTypeText(GoType type) {
        return type.getText();
    }


    static GoStructType getStructType(GoType goType) {
        GoTypeReferenceExpression typeRef = goType.getTypeReferenceExpression();
        return getStruct(typeRef);
    }

    static GoStructType getStructType(GoAnonymousFieldDefinition anonymous) {
        GoTypeReferenceExpression typeRef = anonymous.getTypeReferenceExpression();
        return getStruct(typeRef);
    }

    static GoStructType getStruct(GoTypeReferenceExpression typeRef) {
        PsiElement resolve = typeRef != null ? typeRef.resolve() : null;
        if (resolve instanceof GoTypeSpec typeSpec) {
            GoType type = typeSpec.getSpecType().getType();
            if (type instanceof GoStructType structType) {
                return structType;
            }
        }

        return null;
    }

    private Utils() {
    }
}