package com.qj.entities;

import java.util.Arrays;

/**
 * 评测结果实体类
 * 对应qj_evaluation表
 * 
 * @author system
 */
public class Evaluation {

    private String id;
    private String openid;
    private String shape;
    private SkinColorObj skinColorObj;
    private String typeBeauty;
    private Types[] types;
    private Boolean isDel;

    /**
     * 皮肤颜色对象
     */
    public static class SkinColorObj {
        private String skinColor;
        private String skinColorProportion;

        public String getSkinColor() {
            return skinColor;
        }

        public void setSkinColor(String skinColor) {
            this.skinColor = skinColor;
        }

        public String getSkinColorProportion() {
            return skinColorProportion;
        }

        public void setSkinColorProportion(String skinColorProportion) {
            this.skinColorProportion = skinColorProportion;
        }

        public SkinColorObj() {
        }

        public SkinColorObj(String skinColor, String skinColorProportion) {
            this.skinColor = skinColor;
            this.skinColorProportion = skinColorProportion;
        }

        @Override
        public String toString() {
            return "SkinColorObj{" +
                    "skinColor='" + skinColor + '\'' +
                    ", skinColorProportion='" + skinColorProportion + '\'' +
                    '}';
        }
    }

    /**
     * 类型对象
     */
    public static class Types {
        private String type;
        private String typeProportion;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getTypeProportion() {
            return typeProportion;
        }

        public void setTypeProportion(String typeProportion) {
            this.typeProportion = typeProportion;
        }

        public Types() {
        }

        public Types(String type, String typeProportion) {
            this.type = type;
            this.typeProportion = typeProportion;
        }

        @Override
        public String toString() {
            return "Types{" +
                    "type='" + type + '\'' +
                    ", typeProportion='" + typeProportion + '\'' +
                    '}';
        }
    }

    public String getId() {
        return id;
    }

    public Evaluation setId(String id) {
        this.id = id;
        return this;
    }

    public String getOpenid() {
        return openid;
    }

    public Evaluation setOpenid(String openid) {
        this.openid = openid;
        return this;
    }

    public String getShape() {
        return shape;
    }

    public Evaluation setShape(String shape) {
        this.shape = shape;
        return this;
    }

    public SkinColorObj getSkinColorObj() {
        return skinColorObj;
    }

    public Evaluation setSkinColorObj(SkinColorObj skinColorObj) {
        this.skinColorObj = skinColorObj;
        return this;
    }

    public String getTypeBeauty() {
        return typeBeauty;
    }

    public Evaluation setTypeBeauty(String typeBeauty) {
        this.typeBeauty = typeBeauty;
        return this;
    }

    public Types[] getTypes() {
        return types;
    }

    public Evaluation setTypes(Types[] types) {
        this.types = types;
        return this;
    }

    public Boolean getIsDel() {
        return isDel;
    }

    public Evaluation setIsDel(Boolean isDel) {
        this.isDel = isDel;
        return this;
    }

    public Evaluation() {
    }

    @Override
    public String toString() {
        return "Evaluation{" +
                "id='" + id + '\'' +
                ", openid='" + openid + '\'' +
                ", shape='" + shape + '\'' +
                ", skinColorObj=" + skinColorObj +
                ", typeBeauty='" + typeBeauty + '\'' +
                ", types=" + Arrays.toString(types) +
                ", isDel=" + isDel +
                '}';
    }
}
