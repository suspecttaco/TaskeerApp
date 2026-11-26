package com.simposio.taskeer.data.auth.google

import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialInterruptedException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnknownException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import org.junit.Assert.assertEquals
import org.junit.Test

class GoogleResultMappingTest {

    @Test
    fun cierreDelSelector_esUserCancelled() {
        assertEquals(CredentialClientResult.UserCancelled, credentialFailureOf(GetCredentialCancellationException("x")))
    }

    @Test
    fun interrupcion_esUserCancelled() {
        assertEquals(CredentialClientResult.UserCancelled, credentialFailureOf(GetCredentialInterruptedException("x")))
    }

    @Test
    fun noCredentialException_esNoCredential() {
        assertEquals(CredentialClientResult.NoCredential, credentialFailureOf(NoCredentialException("x")))
    }

    @Test
    fun proveedorNoConfigurado_esProviderMissing() {
        assertEquals(
            CredentialClientResult.ProviderMissing,
            credentialFailureOf(GetCredentialProviderConfigurationException("x")),
        )
    }

    @Test
    fun errorDesconocidoDeCredentialManager_esOther() {
        assertEquals(CredentialClientResult.Other, credentialFailureOf(GetCredentialUnknownException("x")))
    }

    @Test
    fun tokenIlegible_esOther() {
        assertEquals(CredentialClientResult.Other, credentialFailureOf(GoogleIdTokenParsingException(IllegalStateException())))
    }
}
