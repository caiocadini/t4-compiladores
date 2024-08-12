package br.ufscar.dc.compiladores.la.semantico;

import java.util.HashMap;
import java.util.Map;

import br.ufscar.dc.compiladores.la.semantico.TabelaDeSimbolos.TipoLa;

import java.util.List;
import java.util.ArrayList;

public class TabelaDeSimbolos {
    public enum TipoLa {
        INVALIDO,
        INT,
        REAL,
        LIT,
        LOG,
        REG,
        PONTEIROINT,
        PONTEIROREAL,
        ENDERECO,
        FUNCAO,
        PROCEDIMENTO,
        CONSTANTE, 
        VETOR, VAR
    }

    private Map<String, TipoLa> tabela;
    

    public TabelaDeSimbolos() {
        tabela = new HashMap<>();
         
    }

    public void adicionar(String nome, TipoLa tipo) {
        tabela.put(nome, tipo);
    }


    public boolean existe(String nome) {
        return tabela.containsKey(nome);
    }

    public TipoLa verificar(String nome) {
        return tabela.get(nome);
    }

    // Used for debugging
    public Map<String, TipoLa> obterTodosSimbolos() {
        return tabela;
    }
}
