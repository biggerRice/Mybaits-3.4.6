package com.dm.entity;

public class AuthorEntity {

    private Integer id;
    private String name;
    private Integer age;
    /**
     * 一个作者对应多篇文章
     */
    private ArticleEntity[] articles;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    public ArticleEntity[] getArticles() {
        return articles;
    }

    public void setArticles(ArticleEntity[] articles) {
        this.articles = articles;
    }
}