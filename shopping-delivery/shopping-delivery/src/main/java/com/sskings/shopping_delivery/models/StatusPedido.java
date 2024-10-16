package com.sskings.shopping_delivery.models;

public enum StatusPedido {
    PENDENTE("PENDENTE"),
    APROVADO("APROVADO"),
    ENTREGUE("ENTREGUE"),
    CANCELADO("CANCELADO");

    private String status;

    StatusPedido(String status) {
        this.status = status;
    }

    public String getStatus() {
        return status;
    }
}
