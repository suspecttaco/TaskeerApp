package com.simposio.taskeer.domain.workspace

import com.simposio.taskeer.testing.invitation
import com.simposio.taskeer.testing.member
import com.simposio.taskeer.testing.myInvitation
import com.simposio.taskeer.testing.uuid
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InvitationRulesTest {

    private val now = Instant.parse("2026-11-26T10:00:00Z")
    private val past = Instant.parse("2026-11-01T10:00:00Z")
    private val future = Instant.parse("2026-12-20T10:00:00Z")

    private fun validate(raw: String, members: List<WorkspaceMember> = emptyList(), invitations: List<Invitation> = emptyList()) =
        InvitationRules.validateEmail(raw, members, invitations, now)

    @Test
    fun estadoEfectivo_pendienteVencidaEsExpired() {
        assertEquals(InvitationStatus.EXPIRED, InvitationRules.effectiveStatus(InvitationStatus.PENDING, past, now))
        assertEquals(InvitationStatus.EXPIRED, InvitationRules.effectiveStatus(InvitationStatus.PENDING, now, now))
    }

    @Test
    fun estadoEfectivo_pendienteVigente_sigue() {
        assertEquals(InvitationStatus.PENDING, InvitationRules.effectiveStatus(InvitationStatus.PENDING, future, now))
        assertEquals(InvitationStatus.REVOKED, InvitationRules.effectiveStatus(InvitationStatus.REVOKED, past, now))
    }

    @Test
    fun visibles_gestor_soloPendientesYVencidas() {
        val items = listOf(
            invitation(1),
            invitation(2, status = InvitationStatus.ACCEPTED),
            invitation(3, status = InvitationStatus.REVOKED),
            invitation(4, expiresAt = past),
            invitation(5, status = InvitationStatus.EXPIRED),
            invitation(6, status = InvitationStatus.UNKNOWN),
        )

        val ids = InvitationRules.visibleForManagers(items, now).map { it.id }.toSet()

        assertEquals(setOf(uuid(501), uuid(504), uuid(505)), ids)
    }

    @Test
    fun visibles_invitado_soloPendientes() {
        val items = listOf(
            myInvitation(1),
            myInvitation(2, status = InvitationStatus.ACCEPTED),
            myInvitation(3, status = InvitationStatus.REVOKED),
            myInvitation(4, expiresAt = past),
        )

        val ids = InvitationRules.visibleForInvitee(items, now).map { it.id }.toSet()

        assertEquals(setOf(uuid(601), uuid(604)), ids)
    }

    @Test
    fun ordena_pendientesPrimeroYLuegoPorFechaDescendente() {
        val items = listOf(
            invitation(1, expiresAt = past),
            invitation(2),
            invitation(3),
            invitation(4, status = InvitationStatus.EXPIRED),
        )

        val ids = InvitationRules.visibleForManagers(items, now).map { it.id }

        assertEquals(listOf(uuid(503), uuid(502), uuid(504), uuid(501)), ids)
    }

    @Test
    fun ordena_aIgualdadDeFechaDesempataPorId() {
        val createdAt = Instant.parse("2026-11-20T10:00:00Z")
        val items = listOf(invitation(2, createdAt = createdAt), invitation(1, createdAt = createdAt))

        assertEquals(listOf(uuid(501), uuid(502)), InvitationRules.visibleForManagers(items, now).map { it.id })
        assertEquals(
            listOf(uuid(601), uuid(602)),
            InvitationRules.visibleForInvitee(listOf(myInvitation(2, createdAt = createdAt), myInvitation(1, createdAt = createdAt)), now).map { it.id },
        )
    }

    @Test
    fun canInvite_ownerYAdmin_si() {
        assertTrue(InvitationRules.canInvite(WorkspaceRole.OWNER, isPersonal = false))
        assertTrue(InvitationRules.canInvite(WorkspaceRole.ADMIN, isPersonal = false))
    }

    @Test
    fun canInvite_editorViewerUnknown_no() {
        listOf(WorkspaceRole.EDITOR, WorkspaceRole.VIEWER, WorkspaceRole.UNKNOWN).forEach {
            assertFalse(InvitationRules.canInvite(it, isPersonal = false))
        }
    }

    @Test
    fun canInvite_personal_no() {
        assertFalse(InvitationRules.canInvite(WorkspaceRole.OWNER, isPersonal = true))
    }

    @Test
    fun canRevoke_gestorSobrePendiente_si() {
        assertTrue(InvitationRules.canRevoke(WorkspaceRole.OWNER, invitation(1), now))
        assertTrue(InvitationRules.canRevoke(WorkspaceRole.ADMIN, invitation(1), now))
    }

    @Test
    fun canRevoke_sobreVencidaORevocadaOAceptada_no() {
        assertFalse(InvitationRules.canRevoke(WorkspaceRole.OWNER, invitation(1, expiresAt = past), now))
        assertFalse(InvitationRules.canRevoke(WorkspaceRole.OWNER, invitation(2, status = InvitationStatus.REVOKED), now))
        assertFalse(InvitationRules.canRevoke(WorkspaceRole.OWNER, invitation(3, status = InvitationStatus.ACCEPTED), now))
        assertFalse(InvitationRules.canRevoke(WorkspaceRole.OWNER, invitation(4, role = WorkspaceRole.UNKNOWN), now))
    }

    @Test
    fun canRevoke_sinGestor_no() {
        listOf(WorkspaceRole.EDITOR, WorkspaceRole.VIEWER, WorkspaceRole.UNKNOWN).forEach {
            assertFalse(InvitationRules.canRevoke(it, invitation(1), now))
        }
    }

    @Test
    fun canAccept_pendienteVigente_si() {
        assertTrue(InvitationRules.canAccept(myInvitation(1), now))
    }

    @Test
    fun canAccept_vencida_no() {
        assertFalse(InvitationRules.canAccept(myInvitation(1, expiresAt = past), now))
        assertFalse(InvitationRules.canAccept(myInvitation(2, status = InvitationStatus.ACCEPTED), now))
    }

    @Test
    fun verificacion_falsa_pideVerificar() {
        assertEquals(InviterVerification.Unverified, InvitationRules.verification(false))
    }

    @Test
    fun verificacion_nula_permiteIntentar() {
        assertEquals(InviterVerification.Unknown, InvitationRules.verification(null))
    }

    @Test
    fun verificacion_verdadera_permite() {
        assertEquals(InviterVerification.Verified, InvitationRules.verification(true))
    }

    @Test
    fun validarCorreo_vacio() {
        assertEquals(InviteInput.Blank, validate("   "))
    }

    @Test
    fun validarCorreo_malformado() {
        assertEquals(InviteInput.Malformed, validate("no-es-un-correo"))
    }

    @Test
    fun validarCorreo_demasiadoLargo() {
        assertEquals(InviteInput.Malformed, validate("a".repeat(250) + "@example.com"))
    }

    @Test
    fun validarCorreo_conEspaciosEnLosExtremos_seNormaliza() {
        assertEquals(InviteInput.Valid("ana@example.com"), validate("  ana@example.com \n"))
    }

    @Test
    fun validarCorreo_yaMiembroConOtraCapitalizacion() {
        assertEquals(InviteInput.AlreadyMember, validate("M3@Example.com", members = listOf(member(3))))
    }

    @Test
    fun validarCorreo_yaInvitado() {
        assertEquals(InviteInput.AlreadyInvited, validate("I1@example.com", invitations = listOf(invitation(1))))
    }

    @Test
    fun validarCorreo_invitacionVencidaOReocadaNoImpide() {
        val invitations = listOf(
            invitation(1, email = "venc@example.com", expiresAt = past),
            invitation(2, email = "rev@example.com", status = InvitationStatus.REVOKED),
        )

        assertEquals(InviteInput.Valid("venc@example.com"), validate("venc@example.com", invitations = invitations))
        assertEquals(InviteInput.Valid("rev@example.com"), validate("rev@example.com", invitations = invitations))
    }

    @Test
    fun validarCorreo_valido_conservaLaCapitalizacion() {
        assertEquals(InviteInput.Valid("Ana.Perez@Example.com"), validate("Ana.Perez@Example.com"))
    }

    @Test
    fun rolesInvitables_excluyenOwner() {
        assertEquals(listOf(WorkspaceRole.ADMIN, WorkspaceRole.EDITOR, WorkspaceRole.VIEWER), InvitationRules.INVITABLE)
        assertEquals(WorkspaceRole.VIEWER, InvitationRules.DEFAULT_ROLE)
    }

    @Test
    fun reapply_quitaLasRevocadas() {
        val fresh = listOf(invitation(1), invitation(2))

        val result = InvitationRules.reapply(fresh, InvitationConfirmedOps(removedIds = setOf(uuid(501))))

        assertEquals(listOf(uuid(502)), result.map { it.id })
    }

    @Test
    fun reapply_conservaLasNuevas() {
        val fresh = listOf(invitation(1))

        val result = InvitationRules.reapply(fresh, InvitationConfirmedOps(added = listOf(invitation(2), invitation(1))))

        assertEquals(listOf(uuid(502), uuid(501)), result.map { it.id })
    }

    @Test
    fun reapplyMine_quitaLasAceptadas() {
        val result = InvitationRules.reapplyMine(listOf(myInvitation(1), myInvitation(2)), setOf(uuid(601)))

        assertEquals(listOf(uuid(602)), result.map { it.id })
    }
}
