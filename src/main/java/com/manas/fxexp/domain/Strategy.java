package com.manas.fxexp.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class Strategy {

    @Id
    private String id;

    private String fundId;

    protected Strategy() {
    }

    public Strategy(String id, String fundId) {
        this.id = id;
        this.fundId = fundId;
    }

    public String getId() {
        return id;
    }

    public String getFundId() {
        return fundId;
    }
}
