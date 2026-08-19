package com.foodboxd.api.entities;

public enum OwnerRole {
    PRIMARY_OWNER,  // Restoranı sisteme ekleyen asıl sahip — ortak ekleyip çıkarabilir
    CO_OWNER        // Ortak sahip — menü yönetebilir, ortak ekleyemez
}
