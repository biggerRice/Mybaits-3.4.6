package com.dm.dto;

public class ArticleDO {
    private Integer id;
    private String title;
    private String content;

    public ArticleDO(Integer id, String title, String content) {
        this.id = id;
        this.title = title;
        this.content = content;
    }
}