import axios from "axios";
import { toast } from "react-toastify";
import keycloak from "../keycloak";

const GATEWAY = "http://localhost:8080";
const toastOptions = { theme: "colored", position: "top-center" };

// Adds the Keycloak access token to every request sent to the API Gateway,
// refreshing it first if it expires within 30 seconds.
axios.interceptors.request.use(async (config) => {
    if (keycloak.authenticated && config.url && config.url.startsWith(GATEWAY)) {
        try {
            await keycloak.updateToken(30);
            config.headers = config.headers || {};
            config.headers.Authorization = "Bearer " + keycloak.token;
        } catch (e) {
            keycloak.clearToken(); // session expired: do not send an invalid token
        }
    }
    return config;
});

// One place for the errors a user can actually act on.
axios.interceptors.response.use(
    (response) => response,
    (error) => {
        const url = (error.config && error.config.url) || "";
        if (url.startsWith(GATEWAY)) {
            const status = error.response && error.response.status;
            if (!error.response) {
                toast.error("Cannot reach the server. Please check that the backend is running.", toastOptions);
            } else if (status === 401) {
                toast.warning("Your session has expired. Please sign in again.", toastOptions);
                if (keycloak.authenticated) {
                    keycloak.clearToken();
                    keycloak.login({ redirectUri: window.location.href });
                }
            } else if (status === 403) {
                toast.error("You are not allowed to perform this action.", toastOptions);
            } else if (status >= 500) {
                toast.error("Something went wrong on the server. Please try again.", toastOptions);
            }
        }
        return Promise.reject(error);
    }
);
