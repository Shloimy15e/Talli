package dev.dynamiq.talli.service;

/** A validated outbound email identity. */
record EmailSender(String address, String name) {

    String formatted() {
        return name + " <" + address + ">";
    }
}
