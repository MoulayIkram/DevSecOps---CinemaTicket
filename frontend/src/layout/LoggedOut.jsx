import React from 'react'
import keycloak from '../keycloak'

export default function LoggedOut() {
  return (
    <div>
        <ul className="navbar-nav ms-auto py-4 py-lg-0">
            <li class="nav-item">
                <a class="nav-link" href="#!"
                onClick={() => keycloak.register({ redirectUri: window.location.origin })}>Sign Up</a></li>

            <li class="nav-item"><a class="nav-link" href="#!"
                onClick={() => keycloak.login({ redirectUri: window.location.href })}>Sign In</a></li>
        </ul>
    </div>
  )
}
