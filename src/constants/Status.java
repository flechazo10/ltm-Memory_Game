package constants;

public enum Status {
    ONLINE("online"), OFFLINE("offline"), PLAYING("playing");

    private String status;

    Status(String status) {
        this.status = status;
    }

    public String getValue() {
        return status;
    }
}
